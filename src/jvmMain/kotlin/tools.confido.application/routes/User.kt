package tools.confido.application.routes

import com.password4j.Password
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import payloads.requests.*
import payloads.responses.EditProfileResult
import payloads.responses.InviteStatus
import rooms.*
import tools.confido.application.*
import tools.confido.application.sessions.*
import tools.confido.refs.*
import tools.confido.state.*
import tools.confido.store.loginLinkStore
import tools.confido.store.passwordResetLinkStore
import tools.confido.store.questionCommentArchive
import tools.confido.store.roomCommentArchive
import tools.confido.store.transact
import tools.confido.store.verificationLinkStore
import tools.confido.utils.*
import users.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

data class UserContext(val user: User, val session: UserSession, val transientData: TransientData, val call: ApplicationCall)

suspend fun <T> ApplicationCall.withUser(body: suspend UserContext.() -> T): T {
    val session = userSession ?: unauthorized("Not logged in.")
    val user = session.user ?: unauthorized("Not logged in.")
    val transientData = transientUserData ?: unauthorized("Not logged in.")
    return body(UserContext(user, session, transientData, this))
}
suspend fun <T> ApplicationCall.withAdmin(body: suspend UserContext.() -> T): T =
    withUser {
        if (user.type != UserType.ADMIN) unauthorized("This operation requires administrator privileges.")
        body(this@withUser)
    }

fun ApplicationCall.assertSession() = userSession ?: badRequest("Missing session.")

suspend fun ApplicationCall.initSession(user: User, validity: UserSessionValidity) {
    if (userSession != null) {
        destroyUserSession()
    }

    setUserSession(UserSession(userRef = user.ref, validity = validity))
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

        tools.confido.store.userStore.modify(user.ref) {
            it.copy(lastLoginAt = Clock.System.now())
        }

        call.initSession(user, validity = login.validity)
        call.loginUserSession(user, login.validity)

        call.respond(HttpStatusCode.OK)
    }
    // Login by e-mail: Step one (sending link)
    postST("/login_email/create") {
        // TODO: Rate limiting.
        val mail: SendMailLink = call.receive()
        val user = tools.confido.store.userStore.byEmail[mail.email.lowercase()]

        if (user != null && user.active) {
            val expiration = 15.minutes
            val expiresAt = Clock.System.now().plus(expiration)
            val link = LoginLink(token = generateToken(), user = user.ref, expiryTime = expiresAt, url = mail.url, sentToEmail = user.email?.lowercase(), validity = mail.validity)
            // The operation to send an e-mail can fail, do not ma
            try {
                call.mailer.sendLoginMail(mail.email.lowercase(), link, expiration)
            } catch(e: org.simplejavamail.MailException) {
                // Possible side channel!!!
                serviceUnavailable("There was a failure in sending the e-mail.")
            }
            loginLinkStore.insert(link)
        } else {
            // Do not disclose the email does not exist.
        }
        call.respond(HttpStatusCode.OK)
    }
    // Login by e-mail: Step two (using token)
    postST("/login_email") {
        val login: EmailLogin = call.receive()
        val loginLink = loginLinkStore.byToken[login.token] ?: unauthorized("No such token exists.")
        try {
            if (loginLink.isExpired()) { unauthorized("The token has expired.") }
            val user = loginLink.user.deref() ?: unauthorized("The user does not exist.")
            if (!user.active) { unauthorized("The user is deactivated.") }

            tools.confido.store.userStore.modify(loginLink.user) {
                if (loginLink.sentToEmail != null && loginLink.sentToEmail.lowercase() == user.email?.lowercase())
                    // transparently verify email (if it has not changed since)
                    it.copy(lastLoginAt = Clock.System.now(), emailVerified = true)
                else
                    it.copy(lastLoginAt = Clock.System.now())
            }

            call.initSession(user, loginLink.validity)
            call.loginUserSession(user, loginLink.validity)
            call.respond(HttpStatusCode.OK, loginLink.url)
        } finally {
            // Login links are single-use
            loginLinkStore.delete(loginLink.ref, ignoreNonexistent = true)
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
            val login: UsernameLogin = call.receive()
            val user = login.username.deref() ?: unauthorized("The user does not exist.")
            if (!user.active) { unauthorized("The user is deactivated.") }

            tools.confido.store.userStore.modify(user.ref) {
                it.copy(lastLoginAt = Clock.System.now())
            }

            call.initSession(user, login.validity)
            call.loginUserSession(user, login.validity)
            call.respond(HttpStatusCode.OK)
        }
    }
    // Log out
    postST("/logout") {
        // Must run before responding: committing the response also flushes the
        // session cookie, so a later destroyUserSession() cannot clear it.
        val session = call.userSession
        if (session?.user != null) {
            call.destroyUserSession()
        }
        call.respond(HttpStatusCode.OK)
    }
}




fun profileRoutes(routing: Routing) = routing.apply {
    suspend fun UserContext.changeEmail(email: String) {
        val expiration = 15.minutes
        val expiresAt = Clock.System.now().plus(expiration)
        val link = EmailVerificationLink(
            token = generateToken(),
            user = user.ref,
            expiryTime = expiresAt,
            email = email
        )
        try {
            call.mailer.sendVerificationMail(email, link, expiration)
        } catch (e: org.simplejavamail.MailException) {
            serviceUnavailable("Verification e-mail failed to send")
        }

        verificationLinkStore.insert(link)
    }

    suspend fun UserContext.changePassword(currentPassword: String?, newPassword: String) {
        if (user.password != null) {
            if (currentPassword == null || !Password.check(
                    currentPassword,
                    user.password
                ).withArgon2()
            ) unauthorized("The current password is incorrect.")
        }

        when (checkPassword(newPassword)) {
            PasswordCheckResult.OK -> {}
            PasswordCheckResult.TOO_SHORT ->
                badRequest("Password is too short, needs to be at least $MIN_PASSWORD_LENGTH characters long.")
            PasswordCheckResult.TOO_LONG ->
                badRequest("Password is too long, needs to be at most $MAX_PASSWORD_LENGTH characters long.")
        }

        val hash = Password.hash(newPassword).addRandomSalt().withArgon2().result
        tools.confido.store.userStore.modify(user.ref) {
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
        passwordResetLinkStore.insert(link)

        // TODO: Anti password undo spam DDoS

        call.application.log.info("User ${user.ref} changed password.")
    }

    suspend fun UserContext.changeNick(nick: String) {
        val newNick = nick.ifEmpty { null }

        tools.confido.store.userStore.modify(user.ref) {
            it.copy(nick = newNick)
        }
    }


    // E-mail verification: Step one (sending e-mail)
    postST("/profile/email/start_verification") {
        // TODO: Rate limiting
        call.withUser {
            val mail: StartEmailVerification = call.receive()

            if (user.emailVerified && user.email?.lowercase() == mail.email.lowercase())
                badRequest("The e-mail is already verified")

            changeEmail(mail.email)
        }
        call.respond(HttpStatusCode.OK)
    }
    // E-mail verification: Step two (accepting token)
    postST("/profile/email/verify") {
        call.assertSession()

        val validation: TokenVerification = call.receive()
        val verificationLink = verificationLinkStore.byToken[validation.token] ?: unauthorized("No such token exists.")
        if (verificationLink.isExpired()) unauthorized("The token has already expired.")

        tools.confido.store.transact {
            tools.confido.store.userStore.modify(verificationLink.user) {
                it.copy(email = verificationLink.email.lowercase(), emailVerified = true)
            }

            // Verification links are single-use
            verificationLinkStore.delete(verificationLink.ref)
        }

        call.respond(HttpStatusCode.OK)
    }
    // Change password
    postST("/profile/password") {
        call.withUser {
            if (user.email == null) badRequest("You cannot reset password without e-mail.")
            val passwordChange: SetPassword = call.receive()

            changePassword(passwordChange.currentPassword, passwordChange.newPassword)
        }

        call.respond(HttpStatusCode.OK)
    }
    // Remove password
    postST("/profile/password/reset") {
        call.withUser {
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
                //serviceUnavailable("Password reset e-mail failed to send")
                call.application.log.info("The link to undo password reset for ${user.ref} is ${link.link("")}")
            }

            passwordResetLinkStore.insert(link)
        }

        call.respond(HttpStatusCode.OK)
    }
    // Undo password change
    postST("/profile/password/reset/finish") {
        call.assertSession()

        val validation: TokenVerification = call.receive()
        val verificationLink = passwordResetLinkStore.byToken[validation.token] ?: unauthorized("No such token exists.")
        if (verificationLink.isExpired()) unauthorized("The token has already expired.")

        tools.confido.store.transact {
            tools.confido.store.userStore.modify(verificationLink.user) {
                it.copy(password = null)
            }

            // Verification links are single-use
            passwordResetLinkStore.delete(verificationLink.ref)
        }

        // TODO invalidate all user's sessions
        call.respond(HttpStatusCode.OK)
    }
    // Change nick
    postST("/profile/nick") {
        call.withUser {
            val setNick: SetNick = call.receive()
            val newNick = setNick.name.ifEmpty { null }

            tools.confido.store.userStore.modify(user.ref) {
                it.copy(nick = newNick)
            }
        }

        call.respond(HttpStatusCode.OK)
    }

    // Edit profile (everything, redesign)
    postST("/profile/edit") {
        val response = call.withUser {
            val profileData: EditProfile = call.receive()

            var nickChange = false
            if (profileData.nick != user.nick) {
                changeNick(profileData.nick)
                nickChange = true
            }

            var emailChange = false
            var emailError: String? = null
            if (profileData.email.lowercase() != user.email?.lowercase() || !user.emailVerified) {
                try {
                    changeEmail(profileData.email)
                    emailChange = true
                } catch (e: ResponseError) {
                    emailError = e.message
                }
            }

            var passwordChange = false
            var passwordError: String? = null
            if (profileData.newPassword != null) {
                try {
                    changePassword(profileData.currentPassword, profileData.newPassword)
                    passwordChange = true
                } catch (e: ResponseError) {
                    passwordError = e.message
                }
            }

            EditProfileResult(nickChange, emailChange, emailError, passwordChange, passwordError)
        }
        call.respond(HttpStatusCode.OK, response)
    }
}

fun inviteRoutes(routing: Routing) = routing.apply {
    getST("$roomUrl/invite/{token}") {
        val token = call.parameters["token"] ?: ""
        return@getST call.respondRedirect("/join/${token}")
    }
    // /pres/<token> is an alias for /join/<token>?follow=1 (nicer URL for presentation invite links)
    getST("/pres/{token}") {
        val token = call.parameters["token"] ?: return@getST serveFrontend()
        // Resolve shortlinks directly to avoid a double redirect
        if (token.matches(ShortLink.REGEX)) {
            val shortLink = serverState.shortInviteLinks[token]
            if (shortLink != null && shortLink.isValid()) {
                val room = shortLink.room.deref()
                val link = room?.let { r -> r.inviteLinks.firstOrNull { it.id == shortLink.linkId } }
                if (link != null) {
                    return@getST call.respondRedirect("/join/${link.token}?follow=1", permanent = true)
                }
            }
        }
        call.respondRedirect("/join/${token}?follow=1")
    }
    getST("/join/{token}") {
        val token = call.parameters["token"] ?: return@getST serveFrontend() // frontend will display error
        if (token.matches(ShortLink.REGEX)) {
            val shortLink = serverState.shortInviteLinks[token] ?: return@getST serveFrontend()
            if (!shortLink.isValid()) return@getST serveFrontend()
            val room = shortLink.room.deref() ?: return@getST serveFrontend()
            val link = room.inviteLinks.firstOrNull { it.id == shortLink.linkId } ?: return@getST serveFrontend()

            // Send 301 as a hack if user tries to re-visit the original shortcode URL
            // from history after is has already expired.
            // Preserve query params (e.g. ?follow=1) through the redirect.
            val queryString = call.request.queryString().let { if (it.isNotEmpty()) "?$it" else "" }
            return@getST call.respondRedirect("/join/${link.token}$queryString", permanent=true)
        }
        serveFrontend()
    }
    // Verify that this invitation is still valid
    getST("/join/{token}/check") {
        suspend fun invalid() {
            call.respond(HttpStatusCode.OK, InviteStatus(
                valid = false,
                roomName = null,
                roomRef = null,
                roomColor = null,
                nicknamePolicy = InviteFieldPolicy.OPTIONAL,
                emailPolicy = InviteFieldPolicy.OPTIONAL,
                targetUserType = UserType.GUEST,
                preventDuplicateNicknames = false
            ))
        }

        val token = call.parameters["token"] ?: return@getST invalid()

        val linkMatch: (InviteLink)->Boolean = if (token.matches(ShortLink.REGEX)) {
            val shortLink = serverState.shortInviteLinks[token] ?: return@getST invalid()
            if (!shortLink.isValid()) return@getST invalid()
            ({ it.id == shortLink.linkId })
        } else {
            ({ it.token == token })
        }

        val room = serverState.rooms.values.firstOrNull { it.inviteLinks.any(linkMatch) } ?: return@getST invalid()
        val invite = room.inviteLinks.find(linkMatch) ?: return@getST invalid()

        call.respond(HttpStatusCode.OK, InviteStatus(
            valid = true,
            roomName = room.name,
            roomRef = room.ref,
            roomColor = room.color,
            nicknamePolicy = invite.nicknamePolicy,
            emailPolicy = invite.emailPolicy,
            targetUserType = invite.targetUserType,
            preventDuplicateNicknames = invite.preventDuplicateNicknames
        ))
    }
    // Create an invitation link
    postST("$roomUrl/invites/create") {
        val inviteLink = call.withRoom {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members")

            val invited: CreateNewInvite = call.receive()

            if (!canChangeRole(room.userRole(user), invited.role)) unauthorized("This role cannot be changed")
            if (!invited.role.availableToGuests) badRequest("This role cannot be used for invite links.")

            val inviteLink = InviteLink(
                token = generateToken(),
                description = invited.description ?: "",
                role = invited.role,
                createdBy = user.ref,
                createdAt = Clock.System.now(),
                nicknamePolicy = invited.nicknamePolicy,
                emailPolicy = invited.emailPolicy,
                targetUserType = invited.targetUserType,
                // Reset preventDuplicateNicknames to false if nicknamePolicy is DONT_ASK
                preventDuplicateNicknames = if (invited.nicknamePolicy == InviteFieldPolicy.DONT_ASK) false else invited.preventDuplicateNicknames,
                state = InviteLinkState.ENABLED
            )

            tools.confido.store.roomStore.modify(room.id) {
                it.copy(inviteLinks = it.inviteLinks + listOf(inviteLink))
            }
            inviteLink
        }

        call.respond(HttpStatusCode.OK, inviteLink)
    }
    // Edit an invitation link
    postST("$roomUrl/invites/edit") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members")

            val invite: InviteLink = call.receive()

            if (!canChangeRole(room.userRole(user), invite.role)) unauthorized("This role cannot be changed")
            if (!invite.role.availableToGuests) badRequest("This role cannot be used for invite links.")

            tools.confido.store.roomStore.modify(room.ref) { r ->
                val inviteLinks = r.inviteLinks.map {
                    if (invite.id == it.id)
                        it.copy(
                            description = invite.description,
                            role = invite.role,
                            nicknamePolicy = invite.nicknamePolicy,
                            emailPolicy = invite.emailPolicy,
                            targetUserType = invite.targetUserType,
                            // Reset preventDuplicateNicknames to false if nicknamePolicy is DONT_ASK
                            preventDuplicateNicknames = if (invite.nicknamePolicy == InviteFieldPolicy.DONT_ASK) false else invite.preventDuplicateNicknames,
                            state = invite.state
                        )
                    else it
                }
                r.copy(inviteLinks = inviteLinks)
            }
        }

        call.respond(HttpStatusCode.OK)
    }
    // Delete an invitation link
    deleteST("$roomUrl/invite") {
        call.withRoom {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members")

            val delete: DeleteInvite = call.receive()

            tools.confido.store.roomStore.modify(room.ref) { r ->
                val inviteLinks = r.inviteLinks.filterNot { it.id == delete.id }
                val members = r.members.mapNotNull {
                    if (it.invitedVia == delete.id) {
                        if (delete.keepUsers) it.copy(invitedVia = null) else null
                    } else it
                }
                r.copy(inviteLinks = inviteLinks, members = members)
            }
        }

        call.respond(HttpStatusCode.OK)
    }
    // Accept an invitation link as an existing user (requires login)
    postST("$roomUrl/invite/accept") {
        call.withRoom {
            val accept: AcceptInvite = call.receive()
            val invite = room.inviteLinks.find { it.token == accept.inviteToken && it.canJoin }
                ?: unauthorized("The invite does not exist or is currently not active.")

            if (user.email == null && invite.emailPolicy == InviteFieldPolicy.REQUIRED) unauthorized("This invite requires an email.")

            serverState.withMutationLock {
                // Add the user as a member only if they are not yet in
                // XXX: Should we lift the permissions if invitation link has higher permission than the current status?
                // Beware: this is copy-pasted in invite/accept_newuser
                if (room.members.none { it.user eqid user }) {
                    tools.confido.store.roomStore.modify(room.ref) {
                        it.copy(members = it.members + listOf(RoomMembership(user.ref, invite.role, invite.id)))
                    }
                }
            }

            // Start following presentation if requested and a presentation is running
            if (accept.followPresentation && serverState.presentationRunStates.containsKey(room.ref)) {
                call.modifyUserSession { it.copy(followingPresentation = room.ref) }
            }
        }

        call.respond(HttpStatusCode.OK)
    }
    postST("$roomUrl/invite/accept_newuser") {
        if (call.userSession?.user != null) badRequest("You are already logged in.")
        val userAlreadyExists = call.withRoom {
            val accept: AcceptInviteAndCreateUser = call.receive()
            val invite = room.inviteLinks.find { it.token == accept.inviteToken && it.canJoin }
                ?: unauthorized("The invite does not exist or is currently not active.")

            // TODO: Accept happens when not logged in without authentication being required.
            var userAlreadyExists = false
            tools.confido.store.userStore.byEmail[accept.email?.lowercase()]?.let { user ->
                if (room.members.none { it.user eqid user }) {
                    // Beware: this is copy-pasted in invite/accept
                    tools.confido.store.roomStore.modify(room.ref) {
                        it.copy(members = it.members + listOf(RoomMembership(user.ref, invite.role, invite.id)))
                    }
                }
                userAlreadyExists = true
            } ?: run {
                if (invite.emailPolicy == InviteFieldPolicy.REQUIRED && accept.email == null) {
                    badRequest("An email is required.")
                }

                if (invite.nicknamePolicy == InviteFieldPolicy.REQUIRED && accept.userNick == null) {
                    badRequest("A nickname is required.")
                }

                // Sanitize nickname for storage
                val sanitizedNick = accept.userNick?.let { sanitizeNick(it) }

                if (invite.preventDuplicateNicknames && sanitizedNick != null) {
                    // For members, check across workspace
                    if (invite.targetUserType == UserType.MEMBER) {
                        // Compare only agains full members as we do not want a rando guest to be able to block
                        // a name for a full member
                        if (serverState.users.values.any { user ->
                            user.type in listOf(UserType.ADMIN, UserType.MEMBER
                            ) && compareNicks(user.nick, sanitizedNick)
                        }) {
                            badRequest("This nickname is already taken.")
                        }
                    }
                    // For guests, check within room
                    else {
                        if (room.members.any { member ->
                            compareNicks(member.user.deref()?.nick, sanitizedNick)
                        }) {
                            badRequest("This nickname is already taken in this room.")
                        }
                    }
                }

                val newUser = User(
                    randomString(32),
                    invite.targetUserType,
                    accept.email,
                    emailVerified = false,
                    sanitizedNick, // Store sanitized nickname
                    password = null,
                    createdAt = Clock.System.now()
                )

                tools.confido.store.transact {
                    tools.confido.store.userStore.insert(newUser)
                    tools.confido.store.roomStore.modify(room.ref) {
                        it.copy(members = it.members + listOf(RoomMembership(newUser.ref, invite.role, invite.id)))
                    }
                }
                call.initSession(newUser, accept.validity)
                call.loginUserSession(newUser, accept.validity)

                // Start following presentation if requested and a presentation is running
                if (accept.followPresentation && serverState.presentationRunStates.containsKey(room.ref)) {
                    call.modifyUserSession { it.copy(followingPresentation = room.ref) }
                }
            }
            userAlreadyExists
        }

        call.respond(HttpStatusCode.OK, userAlreadyExists)
    }
}

fun adminUserRoutes(routing: Routing) = routing.apply {
    postST("/users/edit") {
        call.withAdmin {
            var editedUser: User = call.receive()
            editedUser = editedUser.copy(email = editedUser.email?.lowercase()?.ifEmpty { null },
                nick = editedUser.nick?.ifEmpty { null })
            if (editedUser.email == null && editedUser.type != UserType.GUEST) badRequest("Only guest can not have e-mail")
            val isSelf = editedUser eqid user

            var duplicateEmail = false
            serverState.withMutationLock {
                editedUser.email?.lowercase()?.let {
                    val emailUser = tools.confido.store.userStore.byEmail[it]
                    if (emailUser != null && !(emailUser eqid editedUser)) {
                        duplicateEmail = true
                        return@withMutationLock
                    }
                }

                tools.confido.store.userStore.modify(editedUser.id) {
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

        call.respond(HttpStatusCode.OK)
    }
    deleteST("/users/{id}") {
        call.withAdmin {
            val userIdToDelete = call.parameters["id"] ?: badRequest("Missing user ID")
            val userToDelete = tools.confido.store.userStore.get(userIdToDelete) ?: notFound("User not found")
            val options: DeleteUserOptions = call.receive()

            if (userToDelete eqid user) unauthorized("Cannot delete your own account")

            // Check if user has any predictions
            val hasPredictions = serverState.userPred.any { (_, userPreds) ->
                userPreds.any { it.key eqid userToDelete }
            }
            // The archives' one non-partition-scoped query: comments by author.
            val userQuestionComments = questionCommentArchive.byAuthor(userToDelete.ref)
            val userRoomComments = roomCommentArchive.byAuthor(userToDelete.ref)
            val hasComments = userQuestionComments.isNotEmpty() || userRoomComments.isNotEmpty()
            val willGhost = hasPredictions || (hasComments && !options.deleteComments)

            transact {
                // Handle comments based on options
                if (hasComments && options.deleteComments) {
                    userQuestionComments.forEach { questionCommentArchive.delete(it) }
                    userRoomComments.forEach { roomCommentArchive.delete(it) }
                }
                // Remove the user's likes from everyone else's comments
                questionCommentArchive.stripUserLikes(userToDelete.ref)
                roomCommentArchive.stripUserLikes(userToDelete.ref)


                // Remove from all rooms
                serverState.rooms.values.forEach { room ->
                    if (room.members.any { it.user eqid userToDelete }) {
                        tools.confido.store.roomStore.modify(room.ref) { r ->
                            r.copy(members = r.members.filterNot { it.user eqid userToDelete })
                        }
                    }
                }

                if (!willGhost)
                serverState.questions.forEach { (_,q)->
                    if (q.author eqid userToDelete) {
                        tools.confido.store.questionStore.modify(q.ref) { it.copy(author = null) }
                    }
                }

                // Delete all sessions for this user
                tools.confido.store.userSessionStore.asMap.values
                    .filter { it.userRef eqid userToDelete }
                    .forEach { session ->
                        tools.confido.store.userSessionStore.delete(session.ref)
                    }
                loginLinkStore.asMap.values
                    .filter { it.user eqid userToDelete }
                    .forEach {
                        loginLinkStore.delete(it.ref)
                    }
                verificationLinkStore.asMap.values
                    .filter { it.user eqid userToDelete }
                    .forEach {
                        verificationLinkStore.delete(it.ref)
                    }
                passwordResetLinkStore.asMap.values
                    .filter { it.user eqid userToDelete }
                    .forEach {
                        passwordResetLinkStore.delete(it.ref)
                    }

                if (willGhost) {
                    // Convert to GHOST user if they have predictions
                    tools.confido.store.userStore.modify(userToDelete.ref) { u ->
                        u.copy(
                            type = UserType.GHOST,
                            email = null,
                            nick = null,
                            password = null,
                            active = false
                        )
                    }
                } else {
                    // If no predictions, we can safely delete
                    tools.confido.store.userStore.delete(userToDelete.ref)
                }
            }

            call.respond(HttpStatusCode.OK)
        }
    }

    postST("/users/add") {
        call.withAdmin {
            val sendInvitationEmail = (call.parameters["invite"] != null)

            var addedUser: User = call.receive()
            addedUser = addedUser.copy(email = addedUser.email?.lowercase()?.ifEmpty { null },
                nick = addedUser.nick?.ifEmpty { null })
            if (addedUser.email == null && addedUser.type != UserType.GUEST) badRequest("Only guest can have empty e-mail")

            var duplicateEmail = false
            serverState.withMutationLock {
                addedUser.email?.lowercase()?.let {
                    val emailUser = tools.confido.store.userStore.byEmail[it]
                    if (emailUser != null && !(emailUser eqid addedUser)) {
                        duplicateEmail = true
                        return@withMutationLock
                    }
                }
                tools.confido.store.userStore.insert(addedUser)
            }
            if (duplicateEmail) badRequest("E-mail address already used by another account")

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
