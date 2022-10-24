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
import tools.confido.refs.Ref
import tools.confido.refs.deref
import tools.confido.refs.eqid
import tools.confido.refs.ref
import tools.confido.state.deleteEntity
import tools.confido.state.insertEntity
import tools.confido.state.modifyEntity
import tools.confido.state.serverState
import tools.confido.utils.randomString
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
            it.email == login.email && it.password != null
                    && Password.check(login.password, it.password).withArgon2()
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
        val user = serverState.userManager.byEmail[mail.email]

        if (user != null) {
            val expiration = 15.minutes
            val expiresAt = Clock.System.now().plus(expiration)
            val link = LoginLink(user = user.ref, expiryTime = expiresAt, url = mail.url)
            // The operation to send an e-mail can fail, do not ma
            try {
                call.mailer.sendLoginMail(mail.email, link, expiration)
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
        if (loginLink.isExpired()) return@postST unauthorized("The token has expired.")

        serverState.withTransaction {
            serverState.userManager.modifyEntity(loginLink.user) {
                it.copy(lastLoginAt = Clock.System.now(), emailVerified = true)
            }
            // Login links are single-use
            serverState.loginLinkManager.deleteEntity(loginLink.ref)
        }

        call.modifyUserSession { it.copy(userRef = loginLink.user) }
        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK, loginLink.url)
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

        if (user.emailVerified && user.email == mail.email)
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
                it.copy(email = verificationLink.email, emailVerified = true)
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
        val newNick = if (setNick.name == "") null else setNick.name

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

        val invite: InviteLink = call.receive()

        if (!room.hasPermission(user, RoomPermission.MANAGE_MEMBERS))
            return@postST call.respond(HttpStatusCode.Unauthorized)

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

        val editedUser: User = call.receive()
        if (editedUser.email == null && editedUser.type != UserType.GUEST) return@postST badRequest("Only guest can not have e-mail")
        val isSelf = editedUser eqid user

        serverState.userManager.modifyEntity(editedUser.id) {
           it.copy(
               nick = editedUser.nick?.ifEmpty { null },
               email = editedUser.email?.ifEmpty { null },
               emailVerified = true,
               type = if (isSelf) UserType.ADMIN else editedUser.type,
               active = if (isSelf) true else editedUser.active,
           )
        }

        TransientData.refreshAllWebsockets()
        call.respond(HttpStatusCode.OK)
    }
    postST("/users/add") {
        val user = call.userSession?.user ?: return@postST unauthorized("Not logged in")
        if (user.type != UserType.ADMIN) return@postST unauthorized("Cannot manage users")

        val sendInvitationEmail = (call.parameters["invite"] != null)

        val addedUser: User = call.receive()
        if (addedUser.email == null && addedUser.type != UserType.GUEST) return@postST badRequest("Only guest can not have e-mail")

        serverState.userManager.insertEntity(addedUser)
        TransientData.refreshAllWebsockets()

        if (sendInvitationEmail && addedUser.email != null) {
            try {
                call.mailer.sendUserInviteMail(addedUser.email)
            } catch(e: org.simplejavamail.MailException) {
                // Possible side channel!!!
                return@postST call.respond(HttpStatusCode.ServiceUnavailable, "There was a failure in sending the e-mail.")
            }
        }

        call.respond(HttpStatusCode.OK)
    }
}
