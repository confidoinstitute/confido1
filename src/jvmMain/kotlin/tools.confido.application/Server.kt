package tools.confido.application

import com.password4j.Password
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Clock.System.now
import kotlinx.html.*
import kotlinx.serialization.encodeToString
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_224
import org.litote.kmongo.serialization.registerModule
import org.litote.kmongo.serialization.registerSerializer
import org.simplejavamail.mailer.MailerBuilder
import payloads.requests.*
import payloads.responses.InviteStatus
import rooms.*
import tools.confido.application.sessions.*
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.*
import tools.confido.refs.*
import tools.confido.serialization.confidoJSON
import tools.confido.serialization.confidoSM
import tools.confido.state.*
import tools.confido.utils.*
import users.*
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.days
import kotlin.collections.any
import kotlin.collections.find
import kotlin.collections.listOf
import kotlin.collections.plus
import kotlin.collections.set

val staticDir = File(System.getenv("CONFIDO_STATIC_PATH") ?: "./build/distributions/").canonicalFile
val jsBundle = staticDir.resolve("confido1.js")
val jsHash = DigestUtils(SHA_224).digestAsHex(jsBundle)

fun HTML.index() {
    head {
        title("Confido")
    }
    body {
        script(src = "/static/confido1.js?${jsHash}") {}
    }
}


// Kotlin coroutines run in a single-threaded mode by default, which is great, because you cannot
// hit accidental race conditions (no concurrent access can occur between suspension points of
// a coroutine).
// Unfortunately, ktor overrides this to use a multi-threaded dispatcher and it is not configurable.
// So all our request handlers must manually enforce a single-threaded dispatcher to ensure safety
// guarantees. To make this a little bit easier, we have created wrapper extension methods for routing
// (getST, postST, webSocketST) that work like get/post/webSocket but automatically enforce
// single-threaded dispatch of their bodies. Except for special cases, we should always use these methods
// instead of the original ones.
// See https://git.confido.institute/confido/confido1/commit/d251e84a9f4e987d379087d8b5e7ac1e0c77e967
val singleThreadContext = newSingleThreadContext("confido_server")
// val singleThreadContext = IO.limitedParallelism(1) // alternative solution


fun Route.getST(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route =
    get(path) { arg-> withContext(singleThreadContext) { body(this@get, arg) } }
fun Route.postST(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route =
    post(path) { arg-> withContext(singleThreadContext) { body(this@post, arg) } }
fun Route.putST(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route =
    put(path) { arg-> withContext(singleThreadContext) { body(this@put, arg) } }
fun Route.deleteST(path: String, body: PipelineInterceptor<Unit, ApplicationCall>): Route =
    delete(path) { arg-> withContext(singleThreadContext) { body(this@delete, arg) } }

fun Route.webSocketST(
    path: String,
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) = webSocket(path, protocol) {  handler(this) }

suspend inline fun PipelineContext<Unit, ApplicationCall>.badRequest(msg: String = "") {
    System.err.println("bad request: ${msg}")
    call.respond(HttpStatusCode.BadRequest, msg)
}

fun main() {
    registerModule(confidoSM)
    // XXX kotlinx.serialization can serialize value classes out of the box. But KMongo uses
    // some low level magic and tries to manage its own serializer repository, and cannot
    // serialize Ref by default. So we must create a custom serializer and also register it
    // with KMongo.
    registerSerializer(RefAsStringSerializer)

    runBlocking { // this is single-threaded by default
        serverState.initialize()
        serverState.load()
    }


    embeddedServer(CIO, port = 8080, host = "127.0.0.1") {
        install(WebSockets)
        install(CallLogging)
        install(ContentNegotiation) {
            this.json(confidoJSON)
        }
        install(Sessions)
        install(Mailing) {
            // TODO(deploy): Set appropriately (*all* options listed here!) and test that emails arrive
            // This is the URL of the hosted frontend used in mailed links (no trailing /)
            urlOrigin = "http://localhost:8081"
            debugMode = false
            senderAddress = "noreply@confido.tools"
            mailer = MailerBuilder
                .withSMTPServer("localhost", 2525)
                .buildMailer()
        }
        routing {
            getST("/{...}") {
                if (call.userSession == null) {
                    call.setUserSession(UserSession())
                }

                call.respondHtml(HttpStatusCode.OK, HTML::index)
            }
            getST("/init/") {
                val userPasswordHash = Password.hash(DebugAdmin.password).addRandomSalt().withArgon2().result
                serverState.userManager.insertEntity(
                    User("debugadmin", UserType.ADMIN, DebugAdmin.email, true, "debugadmin", userPasswordHash, now(), now())
                )
                val memberPasswordHash = Password.hash(DebugMember.password).addRandomSalt().withArgon2().result
                serverState.userManager.insertEntity(
                    User("debugmember", UserType.MEMBER, DebugMember.email, true, "debugmember", memberPasswordHash, now(), now())
                )
                serverState.roomManager.insertEntity(
                    Room("testroom", "Testing room", Clock.System.now(), "This is a testing room.")
                )
            }
            postST("/login") {
                // TODO: Rate limiting.
                val session = call.userSession ?: return@postST badRequest("missing session")

                val login: PasswordLogin = call.receive()
                val user = serverState.users.values.find {
                    it.email == login.email && it.password != null
                            && Password.check(login.password, it.password).withArgon2()
                }

                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@postST
                }

                serverState.userManager.modifyEntity(user.ref) {
                    it.copy(lastLoginAt = now())
                }

                call.modifyUserSession{ it.copy(userRef = user.ref) }
                call.transientUserData?.refreshRunningWebsockets()
                println(session)
                call.respond(HttpStatusCode.OK)
            }
            postST("/login_email/create") {
                // TODO: Rate limiting.
                call.userSession ?: return@postST badRequest("missing session")

                val mail: SendMailLink = call.receive()
                val user = serverState.userManager.byEmail[mail.email]

                if (user != null) {
                    val expiration = 15.minutes
                    val expiresAt = now().plus(expiration)
                    val link = LoginLink(user = user.ref, expiryTime = expiresAt, url = mail.url)
                    serverState.loginLinkManager.insertEntity(link)
                    call.mailer.sendLoginMail(mail.email, link, expiration)
                } else {
                    // Do not disclose the email does not exist.
                }

                call.respond(HttpStatusCode.OK)
            }
            postST("/login_email") {
                val session = call.userSession ?: return@postST badRequest("no session")

                val login: EmailLogin = call.receive()
                val loginLink = serverState.loginLinkManager.byToken[login.token]?.takeIf { !it.isExpired() }
                loginLink ?: return@postST call.respond(HttpStatusCode.Unauthorized)

                serverState.withTransaction {
                    serverState.userManager.modifyEntity(loginLink.user) {
                        it.copy(lastLoginAt = now())
                    }

                    // Login links are single-use
                    serverState.loginLinkManager.deleteEntity(loginLink.ref)
                }

                call.modifyUserSession {  it.copy(userRef = loginLink.user) }
                call.transientUserData?.refreshRunningWebsockets()
                call.respond(HttpStatusCode.OK, loginLink.url)
            }
            postST("/profile/email/start_verification") {
                // TODO: Rate limiting
                val user = call.userSession?.user ?: return@postST badRequest("not logged in")

                val mail: StartEmailVerification = call.receive()

                if (user.emailVerified && user.email == mail.email) {
                    return@postST badRequest("email is already verified")
                }

                val expiration = 15.minutes
                val expiresAt = now().plus(expiration)
                val link = EmailVerificationLink(user = user.ref, expiryTime = expiresAt, email = mail.email)
                serverState.verificationLinkManager.insertEntity(link)
                call.mailer.sendVerificationMail(mail.email, link, expiration)

                call.respond(HttpStatusCode.OK)
            }
            postST("/profile/email/verify") {
                val session = call.userSession ?: return@postST badRequest("no session")

                val validation: EmailVerification = call.receive()
                val verificationLink = serverState.verificationLinkManager.byToken[validation.token]?.takeIf { !it.isExpired() }
                verificationLink ?: return@postST call.respond(HttpStatusCode.Unauthorized)

                serverState.withTransaction {
                    serverState.userManager.modifyEntity(verificationLink.user) {
                        it.copy(email = verificationLink.email, emailVerified = true)
                    }

                    // Verification links are single-use
                    serverState.verificationLinkManager.deleteEntity(verificationLink.ref)
                }

                call.transientUserData?.refreshRunningWebsockets()
                call.respond(HttpStatusCode.OK)
            }
            postST("/logout") {
                val session = call.userSession
                session?.user ?: return@postST badRequest("not logged in") // FIXME: should this just be a NOP?

                call.modifyUserSession {it.copy(userRef = null) }
                call.transientUserData?.refreshRunningWebsockets()
                call.respond(HttpStatusCode.OK)
            }
            postST("/invite/check_status") {
                val check: CheckInvite = call.receive()
                val room = serverState.get<Room>(check.roomId)
                val invite = room?.inviteLinks?.find {it.token == check.inviteToken && it.canJoin}
                if (room == null || invite == null) {
                    call.respond(HttpStatusCode.OK, InviteStatus(false, null))
                    return@postST
                }

                call.respond(HttpStatusCode.OK, InviteStatus(true, room.name))
            }
            postST("/invite/create_email") {
                val invitingUser = call.userSession?.user ?: return@postST badRequest("Not logged in")
                val invite: InviteByEmail = call.receive()
                val room = serverState.get<Room>(invite.roomId) ?: return@postST badRequest("Invalid room")

                if (!room.hasPermission(invitingUser, RoomPermission.MANAGE_MEMBERS)) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@postST
                }

                // Instantly create a user, add it to the group and send a login link
                val loginLink = serverState.withTransaction {
                    val user = serverState.userManager.byEmail[invite.email] ?: run {
                        val newUser = User(
                            randomString(32),
                            UserType.MEMBER,
                            invite.email,
                            emailVerified = false,
                            nick = null,
                            password = null,
                            createdAt = now()
                        )
                        serverState.userManager.insertEntity(newUser)
                        newUser
                    }
                    serverState.roomManager.modifyEntity(room.ref) {
                        it.copy(members = it.members + listOf(RoomMembership(user.ref, invite.role, null)))
                    }

                    val expiration = 14.days
                    val expiresAt = now().plus(expiration)
                    val link = LoginLink(user = user.ref, expiryTime = expiresAt, url = "/room/${room.id}")
                    serverState.loginLinkManager.insertEntity(link)
                    link
                }

                call.mailer.sendDirectInviteMail(invite.email, room, loginLink, invitingUser.email)

                call.transientUserData?.refreshRunningWebsockets()
                call.respond(HttpStatusCode.OK)
            }
            postST("/invite/create") {
                val user = call.userSession?.user ?: return@postST badRequest("Not logged in")

                val create: CreateNewInvite = call.receive()
                val room = serverState.get<Room>(create.roomId) ?: return@postST badRequest("Invalid room")

                if (!room.hasPermission(user, RoomPermission.MANAGE_MEMBERS)) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@postST
                }

                val inviteLink = InviteLink(
                    description = create.description ?: "", role = create.role,
                    createdBy=user.ref, createdAt = now(), anonymous = create.anonymous, state = InviteLinkState.ENABLED
                )
                serverState.roomManager.modifyEntity(room.id) {
                    it.copy(inviteLinks=it.inviteLinks + listOf(inviteLink))
                }

                call.transientUserData?.refreshRunningWebsockets()
                call.respond(HttpStatusCode.OK, inviteLink)
            }
            postST("/rooms/{id}/invites/edit") {
                val user = call.userSession?.user ?: return@postST badRequest("Not logged in")
                val roomRef = Ref<Room>(call.parameters["id"] ?: "")
                val room = roomRef.deref() ?: return@postST badRequest("Room does not exist")

                val invite: InviteLink = call.receive()

                if (!room.hasPermission(user, RoomPermission.MANAGE_MEMBERS))
                    return@postST call.respond(HttpStatusCode.Unauthorized)

                serverState.roomManager.modifyEntity(room.id) { r ->
                    val inviteLinks = r.inviteLinks.map { it ->
                        if (invite.id == it.id)
                            it.copy(
                                description = invite.description,
                                role = invite.role,
                                anonymous = invite.anonymous,
                                state = invite.state
                            )
                        else it
                    }
                    r.copy(inviteLinks=inviteLinks)
                }

                call.transientUserData?.refreshRunningWebsockets()
                call.respond(HttpStatusCode.OK)
            }
            postST("/invite/accept") {
                val user = call.userSession?.user
                if (user == null) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    val accept: AcceptInvite = call.receive()
                    val room = serverState.get<Room>(accept.roomId) ?:
                        return@postST badRequest("The room does not exist.")
                    val invite = room.inviteLinks.find {it.token == accept.inviteToken && it.canJoin} ?:
                        return@postST badRequest("The invite does not exist or is currently not active.")

                    // Prevent user from accepting multiple times
                    if (room.members.any {it.user eqid user && it.invitedVia == invite.id}) {
                        return@postST badRequest("This invite has already been accepted")
                    }

                    serverState.roomManager.modifyEntity(room.ref) {
                        it.copy(members = it.members + listOf(RoomMembership(user.ref, invite.role, invite.id)))
                    }

                    call.transientUserData?.refreshRunningWebsockets()
                    call.respond(HttpStatusCode.OK)
                }
            }
            postST("/invite/accept_newuser") {
                val session = call.userSession
                if (session == null || session.user != null) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    val accept: AcceptInviteAndCreateUser = call.receive()
                    val room = serverState.get<Room>(accept.roomId) ?:
                        return@postST badRequest("The room does not exist.")
                    val invite = room.inviteLinks.find {it.token == accept.inviteToken && it.canJoin} ?:
                        return@postST badRequest("The invite does not exist or is currently not active.")

                    val newUser = User(
                        randomString(32),
                        UserType.GUEST,
                        accept.email,
                        emailVerified = false,
                        accept.userNick,
                        password = null,
                        createdAt = now()
                    )

                    call.modifyUserSession{ it.copy(userRef = newUser.ref) }
                    serverState.withTransaction {
                        serverState.roomManager.modifyEntity(room.ref) {
                            it.copy(members = it.members + listOf(RoomMembership(newUser.ref, invite.role, invite.id)))
                        }
                        serverState.userManager.insertEntity(newUser)
                    }

                    call.transientUserData?.refreshRunningWebsockets()
                    call.respond(HttpStatusCode.OK)
                }
            }
            postST("/setName") {
                val session = call.userSession
                val user = session?.user
                if (session == null || user == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    val setNick: SetNick = call.receive()

                    val editedUser = user.copy(nick = setNick.name)
                    serverState.users[editedUser.id] = editedUser

                    call.transientUserData?.refreshRunningWebsockets()
                    call.respond(HttpStatusCode.OK)
                }
            }
            editQuestion(this)
            postST("/questions/{id}/comments/add") {
                val createdComment: CreateComment = call.receive()
                val id = call.parameters["id"] ?: ""
                val user = call.userSession?.user ?: return@postST badRequest("No user")

                val question = serverState.questions[id] ?: return@postST badRequest("No question")

                if (createdComment.content.isEmpty()) return@postST badRequest("No comment content")

                val prediction = if (createdComment.attachPrediction) {
                    serverState.userPred[question.ref]?.get(user.ref)
                } else {
                    null
                }

                val comment = QuestionComment(question = question.ref, user = user.ref, timestamp = unixNow(),
                                                content = createdComment.content, prediction = prediction)
                serverState.questionCommentManager.insertEntity(comment)
                call.transientUserData?.refreshRunningWebsockets()
                call.respond(HttpStatusCode.OK)
            }
            deleteST("/questions/{qID}/comments/{id}") {
                val qID = call.parameters["qID"] ?: return@deleteST badRequest("No ID")
                val id = call.parameters["id"] ?: return@deleteST badRequest("No ID")
                val user = call.userSession?.user ?: return@deleteST badRequest("No user")

                serverState.withMutationLock {
                    val question = serverState.questions[qID] ?: return@withMutationLock badRequest("No question")
                    val room = serverState.questionRoom[question.ref]?.deref() ?: return@withMutationLock badRequest("No room???")
                    val comment = serverState.questionComments[question.ref]?.get(id) ?: return@withMutationLock badRequest("No comment?????")

                    if (!room.hasPermission(user, RoomPermission.MANAGE_COMMENTS) && !(comment.user eqid user)) return@withMutationLock badRequest("No rights.")

                    serverState.questionCommentManager.deleteEntity(comment, true)

                    call.transientUserData?.refreshRunningWebsockets()
                    call.respond(HttpStatusCode.OK)
                }
            }
            postST("/rooms/{id}/comments/add") {
                val createdComment: CreateComment = call.receive()
                val id = call.parameters["id"] ?: ""
                val user = call.userSession?.user ?: return@postST badRequest("No user")

                val room = serverState.rooms[id] ?: return@postST badRequest("No room")

                if (createdComment.content.isEmpty()) return@postST badRequest("No comment content")

                val comment = RoomComment(id = "", room = room.ref, user = user.ref, timestamp = unixNow(),
                    content = createdComment.content, isAnnotation = false)
                serverState.roomCommentManager.insertEntity(comment)
                call.transientUserData?.refreshRunningWebsockets()
                call.respond(HttpStatusCode.OK)
            }
            deleteST("/rooms/{rID}/comments/{id}") {
                val rID = call.parameters["rID"] ?: return@deleteST badRequest("No ID")
                val id = call.parameters["id"] ?: return@deleteST badRequest("No ID")
                val user = call.userSession?.user ?: return@deleteST badRequest("No user")

                serverState.withMutationLock {
                    val room = serverState.rooms[rID] ?: return@withMutationLock badRequest("No room???")
                    val comment = serverState.roomComments[room.ref]?.get(id) ?: return@withMutationLock badRequest("No comment?????")

                    if (!room.hasPermission(user, RoomPermission.MANAGE_COMMENTS) && !(comment.user eqid user)) return@withMutationLock badRequest("No rights.")

                    serverState.roomCommentManager.deleteEntity(comment, true)

                    call.transientUserData?.refreshRunningWebsockets()
                    call.respond(HttpStatusCode.OK)
                }
            }
            postST("/send_prediction/{id}") {
                val dist: ProbabilityDistribution = call.receive()
                val id = call.parameters["id"] ?: ""
                val user = call.userSession?.user ?: return@postST call.respond(HttpStatusCode.BadRequest)

                val question = serverState.questions[id] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@postST
                }

                if (question.answerSpace != dist.space) {
                    print("Prediction not compatible with answer space")
                    call.respond(HttpStatusCode.BadRequest)
                    return@postST
                }
                val pred = Prediction(ts=unixNow(), dist = dist, question = question.ref, user = user.ref)
                serverState.userPredManager.save(pred)

                call.transientUserData?.refreshRunningWebsockets()
                call.respond(HttpStatusCode.OK)
            }
            webSocketST("/state") {
                // Require a session to already be initialized; it is not possible
                // to edit session cookies within websockets.
                val session = call.userSession
                println(session)

                if (session == null) {
                    // Code 3000 is registered with IANA as "Unauthorized".
                    close(CloseReason(3000, "Missing session"))
                    return@webSocketST
                }

                val closeNotifier = MutableStateFlow(false)

                launch {
                    incoming.receiveCatching().onFailure {
                        closeNotifier.emit(true)
                    }
                }

                call.transientUserData?.runRefreshable(closeNotifier) {
                    val sessionData = call.userSession
                    if (sessionData == null) {
                        closeNotifier.emit(true)
                        return@runRefreshable
                    }

                    val state = StateCensor(sessionData).censor()
                    send(Frame.Text(confidoJSON.encodeToString(state)))
                }
            }
            println("static dir: $staticDir")
            static("/static") {
                staticRootFolder = staticDir
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP) {
                    files(".")
                }
            }
            webSocketST("/state_presenter") {
                val session = call.userSession

                if (session == null) {
                    // Code 3000 is registered with IANA as "Unauthorized".
                    close(CloseReason(3000, "Missing session"))
                    return@webSocketST
                }

                session.presenterActive += 1
                timeout = Duration.ofSeconds(15)
                println("Opened presenter socket for $session")
                send("Connection acquired")
                try {
                    for (message in incoming) {
                        println("Presenter socket pong")
                    }
                } finally {
                    session.presenterActive -= 1
                    println("Closed presenter socket for $session")
                }
            }
        }
    }.start(wait = true)
}
