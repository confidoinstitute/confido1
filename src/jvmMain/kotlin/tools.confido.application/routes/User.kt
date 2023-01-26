package tools.confido.application.routes

import com.password4j.Password
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import payloads.requests.*
import payloads.responses.InviteStatus
import rooms.*
import tools.confido.application.*
import tools.confido.application.sessions.*
import tools.confido.refs.*
import tools.confido.state.*
import tools.confido.utils.*
import users.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

data class UserContext(val user: User, val session: UserSession, val transientData: TransientData)

suspend fun <T> RouteBody.withUser(body: suspend UserContext.() -> T): T {
    val session = call.userSession ?: unauthorized("Not logged in.")
    val user = session.user ?: unauthorized("Not logged in.")
    val transientData = call.transientUserData ?: unauthorized("Not logged in.")
    return body(UserContext(user, session, transientData))
}
suspend fun <T> RouteBody.withAdmin(body: suspend UserContext.() -> T): T =
    withUser {
        if (user.type != UserType.ADMIN) unauthorized("This operation requires administrator privileges.")
        body(this@withUser)
    }

fun RouteBody.assertSession() = call.userSession ?: badRequest("Missing session.")

suspend fun RouteBody.initSession(user: User) {
    if (call.userSession != null) {
        call.destroyUserSession()
    }

    call.setUserSession(UserSession(userRef = user.ref))
}

fun loginRoutes(routing: Routing) = routing.apply {
    // Login by password
    postST("/login") {
        // TODO: Rate limiting.
        val login: PasswordLogin = call.receive()
        val user = serverState.users.values.find {
            it.email?.lowercase() == login.email.lowercase() && it.password != null
                    && Password.check(login.password, it.password).withArgon2()
                    && it.active
        } ?: unauthorized("Wrong e-mail or password")

        serverState.userManager.modifyEntity(user.ref) {
            it.copy(lastLoginAt = Clock.System.now())
        }

        initSession(user)
        call.modifyUserSession { it.copy(userRef = user.ref) }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Login by e-mail: Step one (sending link)
    postST("/login_email/create") {
        // TODO: Rate limiting.
        val mail: SendMailLink = call.receive()
        val user = serverState.userManager.byEmail[mail.email.lowercase()]

        if (user != null && user.active) {
            val expiration = 15.minutes
            val expiresAt = Clock.System.now().plus(expiration)
            val link = LoginLink(token = generateToken(), user = user.ref, expiryTime = expiresAt, url = mail.url, sentToEmail = user.email?.lowercase())
            // The operation to send an e-mail can fail, do not ma
            try {
                call.mailer.sendLoginMail(mail.email.lowercase(), link, expiration)
            } catch(e: org.simplejavamail.MailException) {
                // Possible side channel!!!
                serviceUnavailable("There was a failure in sending the e-mail.")
            }
            serverState.loginLinkManager.insertEntity(link)
        } else {
            // Do not disclose the email does not exist.
        }
        call.respond(HttpStatusCode.OK)
    }
    // Login by e-mail: Step two (using token)
    postST("/login_email") {
        val login: EmailLogin = call.receive()
        val loginLink = serverState.loginLinkManager.byToken[login.token] ?: unauthorized("No such token exists.")
        try {
            if (loginLink.isExpired()) { unauthorized("The token has expired.") }
            val user = loginLink.user.deref() ?: unauthorized("The user does not exist.")
            if (!user.active) { unauthorized("The user is deactivated.") }

            serverState.userManager.modifyEntity(loginLink.user) {
                if (loginLink.sentToEmail != null && loginLink.sentToEmail.lowercase() == user.email?.lowercase())
                    // transparently verify email (if it has not changed since)
                    it.copy(lastLoginAt = Clock.System.now(), emailVerified = true)
                else
                    it.copy(lastLoginAt = Clock.System.now())
            }

            initSession(user)
            call.modifyUserSession { it.copy(userRef = loginLink.user) }
            TransientData.refreshAllWebsockets()
            call.respond(HttpStatusCode.OK, loginLink.url)
        } finally {
            // Login links are single-use
            serverState.loginLinkManager.deleteEntity(loginLink.ref, ignoreNonexistent = true)
        }
    }
    // Login by id (dev mode and demo only; skips login checks and accessible for everyone)
    if (devMode || demoMode) {
        // Login by id: Get user list
        getST("/login_users") {
            val censoredUsers = serverState.users.values.map { it.copy(password = null) }.toTypedArray()
            call.respond(HttpStatusCode.OK, censoredUsers)
        }
        // Login by id: log in by only specifying a user ref
        postST("/login_users") {
            val userRef: Ref<User> = call.receive()
            val user = userRef.deref() ?: unauthorized("The user does not exist.")
            if (!user.active) { unauthorized("The user is deactivated.") }

            serverState.userManager.modifyEntity(user.ref) {
                it.copy(lastLoginAt = Clock.System.now())
            }

            initSession(user)
            call.modifyUserSession { it.copy(userRef = user.ref) }
            TransientData.refreshAllWebsockets()
            call.respond(HttpStatusCode.OK)
        }
    }
    // Log out
    postST("/logout") {
        call.respond(HttpStatusCode.OK)
        val session = call.userSession
        session?.user ?: return@postST

        call.destroyUserSession()
        call.transientUserData?.refreshSessionWebsockets()
    }
}

fun profileRoutes(routing: Routing) = routing.apply {
    // E-mail verification: Step one (sending e-mail)
    postST("/profile/email/start_verification") {
        // TODO: Rate limiting
        withUser {
            val mail: StartEmailVerification = call.receive()

            if (user.emailVerified && user.email?.lowercase() == mail.email.lowercase())
                badRequest("The e-mail is already verified")

            val expiration = 15.minutes
            val expiresAt = Clock.System.now().plus(expiration)
            val link = EmailVerificationLink(
                token = generateToken(),
                user = user.ref,
                expiryTime = expiresAt,
                email = mail.email
            )
            try {
                call.mailer.sendVerificationMail(mail.email, link, expiration)
            } catch (e: org.simplejavamail.MailException) {
                serviceUnavailable("Verification e-mail failed to send")
            }

            serverState.verificationLinkManager.insertEntity(link)
        }
        call.respond(HttpStatusCode.OK)
    }
    // E-mail verification: Step two (accepting token)
    postST("/profile/email/verify") {
        assertSession()

        val validation: TokenVerification = call.receive()
        val verificationLink = serverState.verificationLinkManager.byToken[validation.token] ?: unauthorized("No such token exists.")
        if (verificationLink.isExpired()) unauthorized("The token has already expired.")

        serverState.withTransaction {
            serverState.userManager.modifyEntity(verificationLink.user) {
                it.copy(email = verificationLink.email.lowercase(), emailVerified = true)
            }

            // Verification links are single-use
            serverState.verificationLinkManager.deleteEntity(verificationLink.ref)
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Change password
    postST("/profile/password") {
        withUser {
            if (user.email == null) badRequest("You cannot reset password without e-mail.")
            val passwordChange: SetPassword = call.receive()
            if (user.password != null) {
                if (passwordChange.currentPassword == null || !Password.check(
                        passwordChange.currentPassword,
                        user.password
                    ).withArgon2()
                ) unauthorized("The current password is incorrect.")
            }

            when (checkPassword(passwordChange.newPassword)) {
                PasswordCheckResult.OK -> {}
                PasswordCheckResult.TOO_SHORT ->
                    badRequest("Password is too short, needs to be at least $MIN_PASSWORD_LENGTH characters long.")
                PasswordCheckResult.TOO_LONG ->
                    badRequest("Password is too long, needs to be at most $MAX_PASSWORD_LENGTH characters long.")
            }

            val hash = Password.hash(passwordChange.newPassword).addRandomSalt().withArgon2().result
            serverState.userManager.modifyEntity(user.ref) {
                it.copy(password = hash)
            }

            // Prepare password change notification
            val expiration = 7.days
            val expiresAt = Clock.System.now().plus(expiration)
            val link = PasswordResetLink(
                token = generateToken(),
                user = user.ref,
                expiryTime = expiresAt,
            )
            try {
                if (user.email != null)
                    call.mailer.sendPasswordChangedEmail(user.email, link)
            } catch (e: org.simplejavamail.MailException) {
                //serviceUnavailable("Password change e-mail failed to send")
                call.application.log.info("The link to undo password change for ${user.ref} is ${link.link("")}")
            }
            serverState.passwordResetLinkManager.insertEntity(link)

            // TODO: Anti password undo spam DDoS

            call.application.log.info("User ${user.ref} changed password.")
        }

        call.transientUserData?.refreshSessionWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Remove password
    postST("/profile/password/reset") {
        withUser {
            if (user.email == null) badRequest("You cannot reset password without e-mail.")
            // Prepare password change notification
            val expiration = 15.minutes
            val expiresAt = Clock.System.now().plus(expiration)
            val link = PasswordResetLink(
                token = generateToken(),
                user = user.ref,
                expiryTime = expiresAt,
            )
            try {
                call.mailer.sendPasswordResetEmail(user.email, link, expiration)
            } catch (e: org.simplejavamail.MailException) {
                serviceUnavailable("Password reset e-mail failed to send")
            }

            serverState.passwordResetLinkManager.insertEntity(link)
        }

        call.transientUserData?.refreshSessionWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Undo password change
    postST("/profile/password/reset/finish") {
        assertSession()

        val validation: TokenVerification = call.receive()
        val verificationLink = serverState.passwordResetLinkManager.byToken[validation.token] ?: unauthorized("No such token exists.")
        if (verificationLink.isExpired()) unauthorized("The token has already expired.")

        serverState.withTransaction {
            serverState.userManager.modifyEntity(verificationLink.user) {
                it.copy(password = null)
            }

            // Verification links are single-use
            serverState.passwordResetLinkManager.deleteEntity(verificationLink.ref)
        }

        // TODO invalidate all user's sessions
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Change nick
    postST("/profile/nick") {
        withUser {
            val setNick: SetNick = call.receive()
            val newNick = setNick.name.ifEmpty { null }

            serverState.userManager.modifyEntity(user.ref) {
                it.copy(nick = newNick)
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}

fun inviteRoutes(routing: Routing) = routing.apply {
    getST("/join/{shortcode}") {
        val shortcode = call.parameters["shortcode"] ?: badRequest("Missing invite code (incomplete url entered?)")
        val shortLink = serverState.shortInviteLinks[shortcode] ?: unauthorized("Invalid or expired invite code")
        if (!shortLink.isValid()) unauthorized("Expired invite code")
        val room = shortLink.room.deref() ?: notFound("Room does not exist")
        val link = room.inviteLinks.firstOrNull { it.id == shortLink.linkId } ?: notFound("Invite link does not exist")
        // Send 301 as a hack if user tries to re-visit the original shortcode URL
        // from history after is has already expired.
        call.respondRedirect("/room/${shortLink.room.id}/invite/${link.token}", permanent=true)
    }
    // Verify that this invitation is still valid
    postST("$roomUrl/invite/check") {
        val roomRef = Ref<Room>(call.parameters["rID"] ?: "")
        val room = roomRef.deref() ?: run {
            call.respond(HttpStatusCode.OK, InviteStatus(false, null, false))
            return@postST
        }

        val check: CheckInvite = call.receive()
        val invite = room.inviteLinks.find {it.token == check.inviteToken && it.canJoin}
        if (invite == null) {
            call.respond(HttpStatusCode.OK, InviteStatus(false, null, false))
            return@postST
        }

        call.respond(HttpStatusCode.OK, InviteStatus(true, room.name, invite.allowAnonymous))
    }
    // Create an invitation link
    postST("$roomUrl/invites/create") {
        val inviteLink = withRoom {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members")

            val invited: CreateNewInvite = call.receive()

            if (!canChangeRole(room.userRole(user), invited.role)) unauthorized("This role cannot be changed")
            if (!invited.role.isAvailableToGuests) badRequest("This role cannot be used for invite links.")

            val inviteLink = InviteLink(
                token = generateToken(),
                description = invited.description ?: "",
                role = invited.role,
                createdBy = user.ref,
                createdAt = Clock.System.now(),
                allowAnonymous = invited.anonymous,
                state = InviteLinkState.ENABLED
            )

            serverState.roomManager.modifyEntity(room.id) {
                it.copy(inviteLinks = it.inviteLinks + listOf(inviteLink))
            }
            inviteLink
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK, inviteLink)
    }
    // Edit an invitation link
    postST("$roomUrl/invites/edit") {
        withRoom {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members")

            val invite: InviteLink = call.receive()

            if (!canChangeRole(room.userRole(user), invite.role)) unauthorized("This role cannot be changed")
            if (!invite.role.isAvailableToGuests) badRequest("This role cannot be used for invite links.")

            serverState.roomManager.modifyEntity(room.ref) { r ->
                val inviteLinks = r.inviteLinks.map {
                    if (invite.id == it.id)
                        it.copy(
                            description = invite.description,
                            role = invite.role,
                            allowAnonymous = invite.allowAnonymous,
                            state = invite.state
                        )
                    else it
                }
                r.copy(inviteLinks = inviteLinks)
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Delete an invitation link
    deleteST("$roomUrl/invite") {
        withRoom {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members")

            val delete: DeleteInvite = call.receive()

            serverState.roomManager.modifyEntity(room.ref) { r ->
                val inviteLinks = r.inviteLinks.filterNot { it.id == delete.id }
                val members = r.members.mapNotNull {
                    if (it.invitedVia == delete.id) {
                        if (delete.keepUsers) it.copy(invitedVia = null) else null
                    } else it
                }
                r.copy(inviteLinks = inviteLinks, members = members)
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    // Accept an invitation link as an existing user (requires login)
    postST("$roomUrl/invite/accept") {
        withRoom {
            val accept: AcceptInvite = call.receive()
            val invite = room.inviteLinks.find { it.token == accept.inviteToken && it.canJoin }
                ?: unauthorized("The invite does not exist or is currently not active.")

            if (user.email == null && !invite.allowAnonymous) unauthorized("This invite requires an email.")

            serverState.withMutationLock {
                // Add the user as a member only if they are not yet in
                // XXX: Should we lift the permissions if invitation link has higher permission than the current status?
                // Beware: this is copy-pasted in invite/accept_newuser
                if (room.members.none { it.user eqid user }) {
                    serverState.roomManager.modifyEntity(room.ref) {
                        it.copy(members = it.members + listOf(RoomMembership(user.ref, invite.role, invite.id)))
                    }
                }
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("$roomUrl/invite/accept_newuser") {
        val session = assertSession()
        if (session.user != null) badRequest("You are already logged in.")
        val userAlreadyExists = withRoom {
            val accept: AcceptInviteAndCreateUser = call.receive()
            val invite = room.inviteLinks.find { it.token == accept.inviteToken && it.canJoin }
                ?: unauthorized("The invite does not exist or is currently not active.")

            // TODO: Accept happens when not logged in without authentication being required.
            var userAlreadyExists = false
            serverState.userManager.byEmail[accept.email]?.let { user ->
                if (room.members.none { it.user eqid user }) {
                    // Beware: this is copy-pasted in invite/accept
                    serverState.roomManager.modifyEntity(room.ref) {
                        it.copy(members = it.members + listOf(RoomMembership(user.ref, invite.role, invite.id)))
                    }
                }
                userAlreadyExists = true
            } ?: run {
                if (!invite.allowAnonymous && accept.email == null) {
                    badRequest("An email is required.")
                }

                val newUser = User(
                    randomString(32),
                    UserType.GUEST,
                    accept.email,
                    emailVerified = false,
                    accept.userNick,
                    password = null,
                    createdAt = Clock.System.now()
                )

                serverState.withTransaction {
                    serverState.userManager.insertEntity(newUser)
                    serverState.roomManager.modifyEntity(room.ref) {
                        it.copy(members = it.members + listOf(RoomMembership(newUser.ref, invite.role, invite.id)))
                    }
                }
                call.modifyUserSession { it.copy(userRef = newUser.ref) }
            }
            userAlreadyExists
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK, userAlreadyExists)
    }
}

fun adminUserRoutes(routing: Routing) = routing.apply {
    postST("/users/edit") {
        withAdmin {
            var editedUser: User = call.receive()
            editedUser = editedUser.copy(email = editedUser.email?.lowercase()?.ifEmpty { null },
                nick = editedUser.nick?.ifEmpty { null })
            if (editedUser.email == null && editedUser.type != UserType.GUEST) badRequest("Only guest can not have e-mail")
            val isSelf = editedUser eqid user

            var duplicateEmail = false
            serverState.withMutationLock {
                editedUser.email?.lowercase()?.let {
                    val emailUser = serverState.userManager.byEmail[it]
                    if (emailUser != null && !(emailUser eqid editedUser)) {
                        duplicateEmail = true
                        return@withMutationLock
                    }
                }

                serverState.userManager.modifyEntity(editedUser.id) {
                    it.copy(
                        nick = editedUser.nick?.ifEmpty { null },
                        email = editedUser.email?.lowercase()?.ifEmpty { null },
                        emailVerified = true,
                        type = if (isSelf) UserType.ADMIN else editedUser.type,
                        active = if (isSelf) true else editedUser.active,
                    )
                }
            }
            if (duplicateEmail) badRequest("E-mail address already used by another account")
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("/users/add") {
        withAdmin {
            val sendInvitationEmail = (call.parameters["invite"] != null)

            var addedUser: User = call.receive()
            addedUser = addedUser.copy(email = addedUser.email?.lowercase()?.ifEmpty { null },
                nick = addedUser.nick?.ifEmpty { null })
            if (addedUser.email == null && addedUser.type != UserType.GUEST) badRequest("Only guest can have empty e-mail")

            var duplicateEmail = false
            serverState.withMutationLock {
                addedUser.email?.lowercase()?.let {
                    val emailUser = serverState.userManager.byEmail[it]
                    if (emailUser != null && !(emailUser eqid addedUser)) {
                        duplicateEmail = true
                        return@withMutationLock
                    }
                }
                serverState.userManager.insertEntity(addedUser)
            }
            if (duplicateEmail) badRequest("E-mail address already used by another account")
            TransientData.refreshAllWebsockets()

            if (sendInvitationEmail && addedUser.email != null) {
                try {
                    call.mailer.sendUserInviteMail(addedUser.email!!)
                } catch (e: org.simplejavamail.MailException) {
                    // Possible side channel!!!
                    serviceUnavailable("There was a failure in sending the e-mail.")
                }
            }
        }

        call.respond(HttpStatusCode.OK)
    }
}
