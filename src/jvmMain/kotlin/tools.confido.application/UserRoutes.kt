package tools.confido.application

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
import tools.confido.application.sessions.TransientData
import tools.confido.application.sessions.modifyUserSession
import tools.confido.application.sessions.transientUserData
import tools.confido.application.sessions.userSession
import tools.confido.refs.*
import tools.confido.state.deleteEntity
import tools.confido.state.insertEntity
import tools.confido.state.modifyEntity
import tools.confido.state.serverState
import tools.confido.utils.*
import users.EmailVerificationLink
import users.LoginLink
import users.User
import users.UserType
import kotlin.time.Duration.Companion.minutes

fun loginRoutes(routing: Routing) = routing.apply {
    // Login by password
    postST("/login") {
        // TODO: Rate limiting.
        val session = call.userSession ?: return@postST badRequest("Missing session")

        val login: PasswordLogin = call.receive()
        val user = serverState.users.values.find {
            it.email?.lowercase() == login.email.lowercase() && it.password != null
                    && Password.check(login.password, it.password).withArgon2()
                    && it.active
        } ?: return@postST unauthorized("Wrong e-mail or password")

        serverState.userManager.modifyEntity(user.ref) {
            it.copy(lastLoginAt = Clock.System.now())
        }

        call.modifyUserSession { it.copy(userRef = user.ref) }
        TransientData.refreshAllWebsockets()
        println(session)
        call.respond(HttpStatusCode.OK)
    }
    // Login by e-mail: Step one (sending link)
    postST("/login_email/create") {
        // TODO: Rate limiting.
        val session = call.userSession ?: return@postST badRequest("Missing session")

        val mail: SendMailLink = call.receive()
        val user = serverState.userManager.byEmail[mail.email.lowercase()]

        if (user != null && user.active) {
            val expiration = 15.minutes
            val expiresAt = Clock.System.now().plus(expiration)
            val link = LoginLink(user = user.ref, expiryTime = expiresAt, url = mail.url, sentToEmail = user.email?.lowercase())
            // The operation to send an e-mail can fail, do not ma
            try {
                call.mailer.sendLoginMail(mail.email.lowercase(), link, expiration)
            } catch(e: org.simplejavamail.MailException) {
                // Possible side channel!!!
                return@postST call.respond(HttpStatusCode.ServiceUnavailable, "There was a failure in sending the e-mail.")
            }
            serverState.loginLinkManager.insertEntity(link)
        } else {
            // Do not disclose the email does not exist.
        }
        call.respond(HttpStatusCode.OK)
    }
    // Login by e-mail: Step two (using token)
    postST("/login_email") {
        val session = call.userSession ?: return@postST badRequest("No session")

        val login: EmailLogin = call.receive()
        val loginLink = serverState.loginLinkManager.byToken[login.token] ?:
            return@postST unauthorized("No such token exists.")
        try {
            if (loginLink.isExpired()) {
                return@postST unauthorized("The token has expired.")
            }
            val user = loginLink.user.deref() ?:
                return@postST unauthorized("The user does not exist.")
            if (!user.active) {
                return@postST unauthorized("The user is deactivated.")
            }

            serverState.userManager.modifyEntity(loginLink.user) {
                if (loginLink.sentToEmail != null && loginLink.sentToEmail.lowercase() == user.email?.lowercase())
                    // transparently verify email (if it has not changed since)
                    it.copy(lastLoginAt = Clock.System.now(), emailVerified = true)
                else
                    it.copy(lastLoginAt = Clock.System.now())
            }

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
            call.userSession ?: return@postST badRequest("No session")

            val userRef: Ref<User> = call.receive()
            val user = userRef.deref() ?: return@postST unauthorized("The user does not exist.")
            if (!user.active) {
                return@postST unauthorized("The user is deactivated.")
            }

            serverState.userManager.modifyEntity(user.ref) {
                it.copy(lastLoginAt = Clock.System.now())
            }

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

        call.modifyUserSession { it.copy(userRef = null) }
        call.transientUserData?.refreshSessionWebsockets()
    }
}

fun profileRoutes(routing: Routing) = routing.apply {
    // E-mail verification: Step one (sending e-mail)
    postST("/profile/email/start_verification") {
        // TODO: Rate limiting
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in.")

        val mail: StartEmailVerification = call.receive()

        if (user.emailVerified && user.email?.lowercase() == mail.email.lowercase())
            return@postST badRequest("The e-mail is already verified")

        val expiration = 15.minutes
        val expiresAt = Clock.System.now().plus(expiration)
        val link = EmailVerificationLink(user = user.ref, expiryTime = expiresAt, email = mail.email)
        try {
            call.mailer.sendVerificationMail(mail.email, link, expiration)
        } catch (e: org.simplejavamail.MailException) {
            return@postST call.respond(HttpStatusCode.ServiceUnavailable, "Verification e-mail failed to send")
        }
        serverState.verificationLinkManager.insertEntity(link)
        call.respond(HttpStatusCode.OK)
    }
    // E-mail verification: Step two (accepting token)
    postST("/profile/email/verify") {
        val session = call.userSession ?: return@postST badRequest("no session")

        val validation: EmailVerification = call.receive()
        val verificationLink = serverState.verificationLinkManager.byToken[validation.token] ?:
        return@postST unauthorized("No such token exists.")
        if (verificationLink.isExpired()) return@postST unauthorized("The token has already expired.")

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
    // Change nick
    postST("/profile/nick") {
        val userRef = call.userSession?.userRef ?: return@postST unauthorized("Not logged in.")
        val setNick: SetNick = call.receive()
        val newNick = setNick.name.ifEmpty { null }

        System.err.println("Setting nick $userRef $newNick")

        serverState.userManager.modifyEntity(userRef) {
            it.copy(nick = newNick)
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
}

fun inviteRoutes(routing: Routing) = routing.apply {
    // Verify that this invitation is still valid
    postST("/rooms/{id}/invite/check") {
        val roomRef = Ref<Room>(call.parameters["id"] ?: "")
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
    postST("/rooms/{id}/invites/create") {
        val roomRef = Ref<Room>(call.parameters["id"] ?: "")
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in")
        val room = roomRef.deref() ?: return@postST notFound("Room does not exist")
        if (!room.hasPermission(user, RoomPermission.MANAGE_MEMBERS)) return@postST unauthorized("Cannot manage members")

        val invited: CreateNewInvite = call.receive()
        if (!canChangeRole(room.userRole(user), invited.role)) return@postST unauthorized("This role cannot be changed")

        val inviteLink = InviteLink(
            description = invited.description ?: "", role = invited.role,
            createdBy=user.ref, createdAt = Clock.System.now(), allowAnonymous = invited.anonymous, state = InviteLinkState.ENABLED
        )
        serverState.roomManager.modifyEntity(room.id) {
            it.copy(inviteLinks=it.inviteLinks + listOf(inviteLink))
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK, inviteLink)
    }
    // Edit an invitation link
    postST("/rooms/{id}/invites/edit") {
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in")
        val roomRef = Ref<Room>(call.parameters["id"] ?: "")
        val room = roomRef.deref() ?: return@postST notFound("Room does not exist")
        if (!room.hasPermission(user, RoomPermission.MANAGE_MEMBERS)) return@postST unauthorized("Cannot manage members")

        val invite: InviteLink = call.receive()

        if (!canChangeRole(room.userRole(user), invite.role)) return@postST unauthorized("This role cannot be changed")

        serverState.roomManager.modifyEntity(room.id) { r ->
            val inviteLinks = r.inviteLinks.map { it ->
                if (invite.id == it.id)
                    it.copy(
                        description = invite.description,
                        role = invite.role,
                        allowAnonymous = invite.allowAnonymous,
                        state = invite.state
                    )
                else it
            }
            r.copy(inviteLinks=inviteLinks)
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("/rooms/{id}/invite/accept") {
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in.")
        val roomRef = Ref<Room>(call.parameters["id"] ?: "")
        val room = roomRef.deref() ?: return@postST notFound("This room does not exist.")

        val accept: AcceptInvite = call.receive()
        val invite = room.inviteLinks.find {it.token == accept.inviteToken && it.canJoin} ?:
            return@postST unauthorized("The invite does not exist or is currently not active.")

        serverState.withMutationLock {
            // Add the user as a member only if they are not yet in
            // XXX: Should we lift the permissions if invitation link has higher permission than the current status?
            // Beware: this is copy-pasted in invite/accept_newuser
            if (room.members.none {it.user eqid user}) {
                serverState.roomManager.modifyEntity(room.ref) {
                    it.copy(members = it.members + listOf(RoomMembership(user.ref, invite.role, invite.id)))
                }
            }
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("/rooms/{id}/invite/accept_newuser") {
        val session = call.userSession ?: return@postST badRequest("Mission session")
        if (session.user != null) return@postST badRequest("You are already logged in.")
        val roomRef = Ref<Room>(call.parameters["id"] ?: "")
        val room = roomRef.deref() ?: return@postST notFound("This room does not exist.")

        val accept: AcceptInviteAndCreateUser = call.receive()
        val invite = room.inviteLinks.find {it.token == accept.inviteToken && it.canJoin} ?:
            return@postST unauthorized("The invite does not exist or is currently not active.")


        var userAlreadyExists = false
        serverState.userManager.byEmail[accept.email]?.let {user ->
            if (room.members.none {it.user eqid user}) {
                // Beware: this is copy-pasted in invite/accept
                serverState.roomManager.modifyEntity(room.ref) {
                    it.copy(members = it.members + listOf(RoomMembership(user.ref, invite.role, invite.id)))
                }
            }
            userAlreadyExists = true
        } ?: run {
            if (!invite.allowAnonymous && accept.email == null) {
                return@postST badRequest("An email is required.")
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

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK, userAlreadyExists)
    }
}

fun adminUserRoutes(routing: Routing) = routing.apply {
    postST("/users/edit") {
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in")
        if (user.type != UserType.ADMIN) return@postST unauthorized("Cannot manage users")

        var editedUser: User = call.receive()
        editedUser = editedUser.copy(email = editedUser.email?.lowercase()?.ifEmpty { null }, nick = editedUser.nick?.ifEmpty{null})
        if (editedUser.email == null && editedUser.type != UserType.GUEST) return@postST badRequest("Only guest can not have e-mail")
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
        if (duplicateEmail) return@postST badRequest("E-mail address already used by another account")

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("/users/add") {
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in")
        if (user.type != UserType.ADMIN) return@postST unauthorized("Cannot manage users")

        val sendInvitationEmail = (call.parameters["invite"] != null)

        var addedUser: User = call.receive()
        addedUser = addedUser.copy(email = addedUser.email?.lowercase()?.ifEmpty { null }, nick = addedUser.nick?.ifEmpty{null})
        if (addedUser.email == null && addedUser.type != UserType.GUEST) return@postST badRequest("Only guest can have empty e-mail")

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
        if (duplicateEmail) return@postST badRequest("E-mail address already used by another account")
        TransientData.refreshAllWebsockets()

        if (sendInvitationEmail && addedUser.email != null) {
            try {
                call.mailer.sendUserInviteMail(addedUser.email!!)
            } catch(e: org.simplejavamail.MailException) {
                // Possible side channel!!!
                return@postST call.respond(HttpStatusCode.ServiceUnavailable, "There was a failure in sending the e-mail.")
            }
        }

        call.respond(HttpStatusCode.OK)
    }
}
