package tools.confido.application.routes

import com.password4j.Password
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import tools.confido.payloads.requests.*
import tools.confido.payloads.responses.InviteStatus
import tools.confido.rooms.*
import tools.confido.api.Api
import tools.confido.application.*
import tools.confido.application.sessions.*
import tools.confido.refs.*
import tools.confido.state.*
import tools.confido.store.loginLinkStore
import tools.confido.store.passwordResetLinkStore
import tools.confido.store.emailRevertLinkStore
import tools.confido.store.questionCommentArchive
import tools.confido.store.roomCommentArchive
import tools.confido.store.transact
import tools.confido.store.verificationLinkStore
import tools.confido.utils.*
import tools.confido.users.*
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

/** Delete all of [user]'s sessions except [exceptSessionId]; their websockets close on their own. */
suspend fun invalidateUserSessions(user: Ref<User>, exceptSessionId: String? = null) {
    tools.confido.store.userSessionStore.asMap.values
        .filter { it.userRef eqid user && it.id != exceptSessionId }
        .forEach { tools.confido.store.userSessionStore.delete(it.ref) }
}

suspend fun ApplicationCall.initSession(user: User, validity: UserSessionValidity) {
    if (userSession != null) {
        destroyUserSession()
    }

    setUserSession(UserSession(userRef = user.ref, validity = validity))
}


fun loginRoutes(routing: Routing) = routing.apply {
    // Login by password
    apiST(Api.login) { login ->
        // TODO: Rate limiting.
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
    }
    // Login by e-mail: Step one (sending link)
    apiST(Api.loginEmailCreate) { mail ->
        // TODO: Rate limiting.
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
    }
    // Login by e-mail: Step two (using token); responds with the link's redirect URL
    apiST(Api.loginEmail) { login ->
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
            loginLink.url
        } finally {
            // Login links are single-use
            loginLinkStore.delete(loginLink.ref, ignoreNonexistent = true)
        }
    }
    // Login by id (dev mode and demo only; skips login checks and accessible for everyone)
    if (devMode || demoMode) {
        // Login by id: Get user list
        apiST(Api.loginUsersList) { _ ->
            serverState.users.values.map { it.copy(password = null) }
        }
        // Login by id: log in by only specifying a user ref
        apiST(Api.loginUsers) { login ->
            val user = login.username.deref() ?: unauthorized("The user does not exist.")
            if (!user.active) { unauthorized("The user is deactivated.") }

            tools.confido.store.userStore.modify(user.ref) {
                it.copy(lastLoginAt = Clock.System.now())
            }

            call.initSession(user, login.validity)
            call.loginUserSession(user, login.validity)
        }
    }
    // Log out
    apiST(Api.logout) { _ ->
        // Must run before responding: committing the response also flushes the
        // session cookie, so a later destroyUserSession() cannot clear it.
        val session = call.userSession
        if (session?.user != null) {
            call.destroyUserSession()
        }
    }
}




/** Hash [newPassword] for storage after the shared length policy (badRequest on violation). */
private fun hashNewPassword(newPassword: String): String {
    when (checkPassword(newPassword)) {
        PasswordCheckResult.OK -> {}
        PasswordCheckResult.TOO_SHORT ->
            badRequest("Please choose a password at least $MIN_PASSWORD_LENGTH characters long.")
        PasswordCheckResult.TOO_LONG ->
            badRequest("Please choose a password at most $MAX_PASSWORD_LENGTH characters long.")
    }
    return Password.hash(newPassword).addRandomSalt().withArgon2().result
}

private suspend fun deleteVerificationLinks(user: Ref<User>) =
    verificationLinkStore.asMap.values.filter { it.user eqid user }.forEach { verificationLinkStore.delete(it.ref) }

private suspend fun deletePasswordResetLinks(user: Ref<User>) =
    passwordResetLinkStore.asMap.values.filter { it.user eqid user }.forEach { passwordResetLinkStore.delete(it.ref) }

/**
 * Profile self-service. Nickname, e-mail and password are independent actions, each
 * with its own endpoint (and its own save button in the UI): no partial-success
 * bundles. Mailed links carry the authority for their action, so the link endpoints
 * need no session — they are often opened in another browser (a phone's mail app).
 */
fun profileRoutes(routing: Routing) = routing.apply {
    apiST(Api.profileNick) { setNick ->
        call.withUser {
            val newNick = sanitizeNick(setNick.name).ifEmpty { null }
            if (newNick != user.nick)
                tools.confido.store.userStore.modify(user.ref) { it.copy(nick = newNick) }
        }
    }

    // E-mail change, step one: mail a verification link to the new address. The address
    // replaces the current one only once verified; until then it is pending
    // (SelfState.myPendingEmail). Also re-sends verification for an unverified current address.
    apiST(Api.profileEmailStartVerification) { mail ->
        // TODO: Rate limiting
        call.withUser {
            val email = mail.email.trim().lowercase()
            if (!isEmailValid(email)) badRequest("This is not a valid e-mail address.")
            if (user.emailVerified && email == user.email?.lowercase())
                badRequest("This is already your e-mail address.")
            // Proper users see every address in the users directory anyway, so telling them
            // now leaks nothing; guests learn it only at verification (inbox owners only).
            if (user.type.isProper()) {
                val holder = tools.confido.store.userStore.byEmail[email]
                if (holder != null && !(holder eqid user))
                    badRequest("This e-mail address is already used by another account.")
            }

            val link = EmailVerificationLink(
                token = generateToken(),
                user = user.ref,
                expiryTime = Clock.System.now() + EMAIL_VERIFICATION_VALIDITY,
                email = email,
            )
            try {
                call.mailer.sendVerificationMail(email, link, EMAIL_VERIFICATION_VALIDITY)
            } catch (e: org.simplejavamail.MailException) {
                serviceUnavailable("The verification e-mail failed to send.")
            }
            tools.confido.store.transact {
                // Only the newest link counts: an older one could switch the address back.
                deleteVerificationLinks(user.ref)
                verificationLinkStore.insert(link)
            }
        }
    }
    // E-mail change, step two: the link's token is the credential (no session needed).
    apiST(Api.profileEmailVerify) { validation ->
        val verificationLink = verificationLinkStore.byToken[validation.token]
            ?: unauthorized("This link is invalid or was already used.")
        if (verificationLink.isExpired()) unauthorized("This link has expired.")
        // Checked here rather than when sending: only the inbox owner learns the address is taken.
        val holder = tools.confido.store.userStore.byEmail[verificationLink.email.lowercase()]
        if (holder != null && !(holder eqid verificationLink.user))
            badRequest("This e-mail address is already used by another account.")

        val previous = verificationLink.user.deref()?.email
        val newEmail = verificationLink.email.lowercase()
        tools.confido.store.transact {
            tools.confido.store.userStore.modify(verificationLink.user) {
                it.copy(email = newEmail, emailVerified = true)
            }
            deleteVerificationLinks(verificationLink.user)
        }

        // A session alone suffices to move the account to another inbox, so the previous
        // one gets a "not you?" notice with a way back (no confirmation required there:
        // losing the old inbox is a main reason to change the address).
        if (previous != null && previous.lowercase() != newEmail) {
            val now = Clock.System.now()
            val link = EmailRevertLink(
                token = generateToken(),
                user = verificationLink.user,
                email = previous.lowercase(),
                createdAt = now,
                expiryTime = now + EMAIL_CHANGE_REVERT_VALIDITY,
            )
            try {
                call.mailer.sendEmailChangedEmail(previous, newEmail, link, EMAIL_CHANGE_REVERT_VALIDITY)
            } catch (e: org.simplejavamail.MailException) {
                call.application.log.info("The link to revert the e-mail change of ${verificationLink.user} is ${link.link("")}")
            }
            emailRevertLinkStore.insert(link)
        }
    }
    // E-mail change "not you?": restore the previous address and lock the account down —
    // every session ends (the opener's too: a stolen cookie *is* the opener's session),
    // the password goes (the intruder may have set it), pending links die.
    apiST(Api.profileEmailRevert) { revert ->
        val revertLink = emailRevertLinkStore.byToken[revert.token]
            ?: unauthorized("This link is invalid or was cancelled by an earlier one.")
        if (revertLink.usedAt != null) return@apiST // repeated open (e.g. the page remounting logged out)
        if (revertLink.isExpired()) unauthorized("This link has expired.")
        val holder = tools.confido.store.userStore.byEmail[revertLink.email]
        if (holder != null && !(holder eqid revertLink.user))
            badRequest("This e-mail address is now used by another account. Please contact an administrator.")

        tools.confido.store.transact {
            tools.confido.store.userStore.modify(revertLink.user) {
                it.copy(email = revertLink.email, emailVerified = true, password = null)
            }
            // Later links stem from later changes, possibly the intruder's own; earlier ones
            // (the owner's) stay usable, so the original owner always gets the last word.
            emailRevertLinkStore.asMap.values
                .filter { it.user eqid revertLink.user && it.createdAt > revertLink.createdAt }
                .forEach { emailRevertLinkStore.delete(it.ref) }
            emailRevertLinkStore.modify(revertLink.ref) { it.copy(usedAt = Clock.System.now()) }
            deleteVerificationLinks(revertLink.user)
            deletePasswordResetLinks(revertLink.user)
            loginLinkStore.asMap.values.filter { it.user eqid revertLink.user }.forEach { loginLinkStore.delete(it.ref) }
            invalidateUserSessions(revertLink.user)
        }
        call.application.log.info("User ${revertLink.user} reverted an e-mail change to ${revertLink.email}.")
    }
    apiST(Api.profileEmailCancel) { _ ->
        call.withUser { deleteVerificationLinks(user.ref) }
    }

    /** Once a password is set, changing or removing it requires it (badRequest otherwise). */
    fun UserContext.requireCurrentPassword(current: String?) {
        val stored = user.password ?: return
        if (current.isNullOrEmpty()) badRequest("Please enter your current password.")
        if (!Password.check(current, stored).withArgon2()) badRequest("The current password is incorrect.")
    }

    /** Store [hash] (null = no password) and end the user's other sessions, then mail the
     *  owner a "not you?" notice carrying a reset link. */
    suspend fun UserContext.replacePassword(hash: String?) {
        tools.confido.store.userStore.modify(user.ref) { it.copy(password = hash) }
        invalidateUserSessions(user.ref, exceptSessionId = session.id)

        val link = PasswordResetLink(
            token = generateToken(),
            user = user.ref,
            expiryTime = Clock.System.now() + PASSWORD_CHANGED_UNDO_VALIDITY,
        )
        try {
            user.email?.let { call.mailer.sendPasswordChangedEmail(it, link, removed = hash == null) }
        } catch (e: org.simplejavamail.MailException) {
            call.application.log.info("The link to undo password change for ${user.ref} is ${link.link("")}")
        }
        passwordResetLinkStore.insert(link)
        // TODO: Anti password undo spam DDoS
        call.application.log.info("User ${user.ref} ${if (hash == null) "removed" else "changed"} password.")
    }

    // Set or change the password (the current one is required once one is set).
    apiST(Api.profilePassword) { passwordChange ->
        call.withUser {
            if (user.email == null) badRequest("Add an e-mail address first: you log in with it and the password.")
            requireCurrentPassword(passwordChange.currentPassword)
            replacePassword(hashNewPassword(passwordChange.newPassword))
        }
    }
    // Remove the password: back to e-mail-link login, the state every account starts in.
    apiST(Api.profilePasswordRemove) { removal ->
        call.withUser {
            if (user.password == null) badRequest("You have no password set.")
            requireCurrentPassword(removal.currentPassword)
            replacePassword(null)
        }
    }
    // Password reset, step one: mail a link for choosing a new password without the
    // current one. (Logged out, e-mail login already covers a forgotten password.)
    apiST(Api.profilePasswordReset) { _ ->
        // TODO: Rate limiting
        call.withUser {
            val email = user.email ?: badRequest("Your account has no e-mail address to send the link to.")
            val link = PasswordResetLink(
                token = generateToken(),
                user = user.ref,
                expiryTime = Clock.System.now() + PASSWORD_RESET_VALIDITY,
            )
            try {
                call.mailer.sendPasswordResetEmail(email, link, PASSWORD_RESET_VALIDITY)
            } catch (e: org.simplejavamail.MailException) {
                serviceUnavailable("The password reset e-mail failed to send.")
            }
            passwordResetLinkStore.insert(link)
        }
    }
    // Password reset, step two (also the target of the "password changed" mail): set the
    // new password and log everyone else out — whoever changed it may hold a session.
    apiST(Api.profilePasswordResetFinish) { reset ->
        val resetLink = passwordResetLinkStore.byToken[reset.token]
            ?: unauthorized("This link is invalid or was already used.")
        if (resetLink.isExpired()) unauthorized("This link has expired.")
        resetLink.user.deref() ?: unauthorized("The user does not exist.")
        val hash = hashNewPassword(reset.newPassword) // before consuming the link

        tools.confido.store.transact {
            tools.confido.store.userStore.modify(resetLink.user) { it.copy(password = hash) }
            deletePasswordResetLinks(resetLink.user)
            // The opener's own session survives if it is the same user (inbox owner).
            val ownSession = call.userSession?.takeIf { it.userRef eqid resetLink.user }
            invalidateUserSessions(resetLink.user, exceptSessionId = ownSession?.id)
        }
        call.application.log.info("User ${resetLink.user} reset password via link.")
    }

    // Set/clear one personal UI preference (bounded: these are tiny display prefs).
    apiST(Api.profileUiSetting) { req ->
        call.withUser {
            if (req.key.length > 64 || (req.value?.length ?: 0) > 64) badRequest("Setting too long.")
            tools.confido.store.userStore.modify(user.ref) {
                val newSettings =
                    if (req.value == null) it.uiSettings - req.key
                    else it.uiSettings + (req.key to req.value)
                if (newSettings.size > 200) badRequest("Too many UI settings.")
                it.copy(uiSettings = newSettings)
            }
        }
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
    apiST(Api.joinCheck) { token, _ ->
        val invalid = InviteStatus(
            valid = false,
            roomName = null,
            roomRef = null,
            roomColor = null,
            nicknamePolicy = InviteFieldPolicy.OPTIONAL,
            emailPolicy = InviteFieldPolicy.OPTIONAL,
            targetUserType = UserType.GUEST,
            preventDuplicateNicknames = false
        )

        val linkMatch: (InviteLink)->Boolean = if (token.matches(ShortLink.REGEX)) {
            val shortLink = serverState.shortInviteLinks[token] ?: return@apiST invalid
            if (!shortLink.isValid()) return@apiST invalid
            ({ it.id == shortLink.linkId })
        } else {
            ({ it.token == token })
        }

        val room = serverState.rooms.values.firstOrNull { it.inviteLinks.any(linkMatch) } ?: return@apiST invalid
        val invite = room.inviteLinks.find(linkMatch) ?: return@apiST invalid

        InviteStatus(
            valid = true,
            roomName = room.name,
            roomRef = room.ref,
            roomColor = room.color,
            nicknamePolicy = invite.nicknamePolicy,
            emailPolicy = invite.emailPolicy,
            targetUserType = invite.targetUserType,
            preventDuplicateNicknames = invite.preventDuplicateNicknames
        )
    }
    // Create an invitation link; responds with the created link
    apiST(Api.inviteCreate) { rRef, invited ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members")

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
    }
    // Edit an invitation link
    apiST(Api.inviteEdit) { rRef, invite ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members")

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
    }
    // Delete an invitation link
    apiST(Api.inviteDelete) { rRef, delete ->
        call.withRoom(rRef) {
            assertPermission(RoomPermission.MANAGE_MEMBERS, "Cannot manage members")

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
    }
    // Accept an invitation link as an existing user (requires login)
    apiST(Api.inviteAccept) { rRef, accept ->
        call.withRoom(rRef) {
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
    }
    // Responds true if a user with the given email already existed
    apiST(Api.inviteAcceptNewUser) { rRef, accept ->
        if (call.userSession?.user != null) badRequest("You are already logged in.")
        call.withRoom(rRef) {
            val invite = room.inviteLinks.find { it.token == accept.inviteToken && it.canJoin }
                ?: unauthorized("The invite does not exist or is currently not active.")

            val acceptEmail = accept.email?.trim()?.lowercase()?.ifEmpty { null }
            if (acceptEmail != null && !isEmailValid(acceptEmail)) badRequest("This is not a valid e-mail address.")

            // TODO: Accept happens when not logged in without authentication being required.
            var userAlreadyExists = false
            tools.confido.store.userStore.byEmail[acceptEmail]?.let { user ->
                if (room.members.none { it.user eqid user }) {
                    // Beware: this is copy-pasted in invite/accept
                    tools.confido.store.roomStore.modify(room.ref) {
                        it.copy(members = it.members + listOf(RoomMembership(user.ref, invite.role, invite.id)))
                    }
                }
                userAlreadyExists = true
            } ?: run {
                if (invite.emailPolicy == InviteFieldPolicy.REQUIRED && acceptEmail == null) {
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
                    acceptEmail,
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
    }
}

/** Admin add/edit input, held to the same rules as self-service profile edits. */
private fun User.normalizedByAdmin(): User {
    val email = email?.trim()?.lowercase()?.ifEmpty { null }
    if (email == null && type != UserType.GUEST) badRequest("Only guests can be without an e-mail address.")
    if (email != null && !isEmailValid(email)) badRequest("This is not a valid e-mail address.")
    return copy(email = email, nick = nick?.let { sanitizeNick(it) }?.ifEmpty { null })
}

fun adminUserRoutes(routing: Routing) = routing.apply {
    apiST(Api.userEdit) { receivedUser ->
        call.withAdmin {
            val editedUser = receivedUser.normalizedByAdmin()
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
                        nick = editedUser.nick,
                        email = editedUser.email,
                        // An admin-entered address counts as verified; untouched ones keep their state.
                        emailVerified = if (editedUser.email != it.email) editedUser.email != null else it.emailVerified,
                        type = if (isSelf) UserType.ADMIN else editedUser.type,
                        active = if (isSelf) true else editedUser.active,
                    )
                }
            }
            if (duplicateEmail) badRequest("E-mail address already used by another account")
        }
    }
    apiST(Api.userDelete) { uRef, options ->
        call.withAdmin {
            val userToDelete = tools.confido.store.userStore.get(uRef.id) ?: notFound("User not found")

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
                invalidateUserSessions(userToDelete.ref)
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
                emailRevertLinkStore.asMap.values
                    .filter { it.user eqid userToDelete }
                    .forEach {
                        emailRevertLinkStore.delete(it.ref)
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
        }
    }

    apiST(Api.userAdd) { receivedUser ->
        call.withAdmin {
            val sendInvitationEmail = (call.parameters["invite"] != null)

            // Passwords are only ever set (hashed) by their owner via the profile routes.
            val addedUser = receivedUser.normalizedByAdmin().let { it.copy(emailVerified = it.email != null, password = null) }

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
    }
}
