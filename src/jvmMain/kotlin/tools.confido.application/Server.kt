package tools.confido.application

import com.password4j.Password
import tools.confido.refs.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.LocalDate
import kotlinx.html.*
import kotlinx.serialization.encodeToString
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.serialization.registerModule
import payloads.requests.*
import payloads.responses.InviteStatus
import rooms.*
import tools.confido.application.sessions.*
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.state.SentState
import tools.confido.state.UserSession
import tools.confido.question.*
import tools.confido.serialization.confidoJSON
import tools.confido.serialization.confidoSM
import tools.confido.spaces.*
import tools.confido.state.serverState
import tools.confido.utils.*
import users.DebugAdmin
import users.User
import users.UserType
import java.io.File
import java.time.Duration

fun HTML.index() {
    head {
        title("Confido")
    }
    body {
        script(src = "/static/confido1.js") {}
    }
}

// object ServerState {
//     // TODO: These maps should be concurrent.
//     var rooms: Map<String, Room> = emptyMap()
//     var questions: MutableMap<String, Question> = mutableMapOf()
//     val userPredictions: MutableMap<User, MutableMap<String, Prediction>> = mutableMapOf()
//     var groupPredictions: MutableMap<String, List<Double>> = mutableMapOf()
//     var comments: MutableMap<String, MutableList<Comment>> = mutableMapOf()
//     var users: MutableMap<String, User> = mutableMapOf()
//
//     fun loadQuestions(collection: CoroutineCollection<Question>) {
//         questions = runBlocking {
//             collection.find().toList().associateBy { question -> question.id }.toMutableMap()
//         }
//
//         // TODO: Remove this user (and the DebugAdmin object + all usages).
//         val passwordHash = Password.hash(DebugAdmin.password).addRandomSalt().withArgon2().result
//         val debugAdmin = User("debug", UserType.ADMIN, DebugAdmin.email, true, "debugadmin", passwordHash, now(), now())
//         users[debugAdmin.id] = debugAdmin
//
//         // TODO: Persist rooms, for now we create one room that contains all questions and one "private" room with a new question
//         val pub = "testpub" to Room("testpub", "Testing room", now(), questions = questions.values.map {it.ref}.toMutableList())
//         val qtestpriv = Question("qtestpriv", "Is this a private question?", BinarySpace)
//         questions["qtestpriv"] = qtestpriv
//         val priv = "testpriv" to Room("testpriv", "Private room", now(), description = "A private room.", questions = mutableListOf(qtestpriv.ref))
//         val testInvite = InviteLink("Testing invite link", Forecaster, debugAdmin, now())
//         priv.second.inviteLinks.add(testInvite)
//         println("Testing invite: ${testInvite.token}")
//         rooms = mapOf(pub, priv)
//
//         // TODO actually store comments!
//         comments = questions.mapValues { mutableListOf<Comment>() }.toMutableMap()
//         calculateGroupDistribution()
//     }
//
//     fun calculateGroupDistribution(question: Question) {
//         //groupDistributions[question.id] = userPredictions.values.mapNotNull {
//         //    it[question.id]?.let { prediction -> question.answerSpace.predictionToDistribution(prediction) }
//         //}.fold(List(question.answerSpace.bins) { 0.0 }) { acc, dist ->
//         //    dist.zip(acc) { a, b -> a + b }
//         //}
//     }
//
//     fun calculateGroupDistribution() {
//         questions.mapValues { (_, question) -> calculateGroupDistribution(question) }
//     }
//
//     fun getUserPredictions(user: User): MutableMap<String, Prediction> {
//         return userPredictions.getOrPut(user) {mutableMapOf()}
//     }
//
//     fun appState(sessionData: UserSession): SentState {
//         val predictions = when (val user = sessionData.user) {
//             null -> emptyMap()
//             else -> getUserPredictions(user)
//         }
//
//         return SentState(
//             // TODO: Consider sending questions separately and only provide ids within rooms
//             rooms.values.filter { it.hasPermission(sessionData.user, RoomPermission.VIEW_QUESTIONS) },
//             predictions,
//             comments,
//             emptyMap(),//groupDistributions.toMap(),
//             sessionData,
//         )
//     }
// }

fun main() {
    registerModule(confidoSM)


    runBlocking { serverState.load() }

    embeddedServer(CIO, port = 8080, host = "127.0.0.1") {
        install(WebSockets)
        install(CallLogging)
        install(ContentNegotiation) {
            this.json(confidoJSON)
        }
        install(Sessions)
        routing {
            get("/{...}") {
                if (call.userSession == null) {
                    call.userSession = UserSession()
                }

                call.respondHtml(HttpStatusCode.OK, HTML::index)
            }
            post("/login") {
                val session = call.userSession
                if (session == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val login: Login = call.receive()
                val user = serverState.users.values.find {
                    it.email == login.email && it.password != null
                            && Password.check(login.password, it.password).withArgon2()
                }

                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                session.userRef = user.ref
                call.transientUserData?.refreshRunningWebsockets()
                call.respond(HttpStatusCode.OK)
            }
            post("/logout") {
                val session = call.userSession
                if (session?.user == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                session.userRef = null
                call.transientUserData?.refreshRunningWebsockets()
                call.respond(HttpStatusCode.OK)
            }
            post("/invite/check_status") {
                val check: CheckInvite = call.receive()
                val room = serverState.rooms[check.roomId]
                val invite = room?.inviteLinks?.find {it.token == check.inviteToken && it.canJoin}
                if (room == null || invite == null) {
                    call.respond(HttpStatusCode.OK, InviteStatus(false, null))
                    return@post
                }

                call.respond(HttpStatusCode.OK, InviteStatus(true, room.name))
            }
            post("/invite/accept") {
                val user = call.userSession?.user
                if (user == null) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    val accept: AcceptInvite = call.receive()
                    val room = serverState.rooms[accept.roomId] ?: return@post
                    val invite = room.inviteLinks.find {it.token == accept.inviteToken && it.canJoin} ?: return@post

                    // TODO: Prevent user from accepting multiple times
                    call.userSession = UserSession(userRef = user.ref, language = "en")
                    room.members.add(RoomMembership(user, invite.role, invite))

                    call.transientUserData?.refreshRunningWebsockets()
                    call.respond(HttpStatusCode.OK)
                }
            }
            post("/invite/accept_newuser") {
                val session = call.userSession
                if (session == null || session.user != null) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    val accept: AcceptInviteAndCreateUser = call.receive()
                    val room = serverState.rooms[accept.roomId] ?: return@post
                    val invite = room.inviteLinks.find {it.token == accept.inviteToken && it.canJoin} ?: return@post

                    val newUser = User(randomString(32), UserType.GUEST, accept.email, false, accept.userNick, null, now(), now())

                    call.userSession = UserSession(userRef = newUser.ref, language = "en")
                    room.members.add(RoomMembership(newUser, invite.role, invite))
                    serverState.users.insert(newUser)

                    call.transientUserData?.refreshRunningWebsockets()
                    call.respond(HttpStatusCode.OK)
                }
            }
            post("/setName") {
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
            post("/add_comment/{id}") {
                val createdComment: CreateComment = call.receive()
                val id = call.parameters["id"] ?: ""
                val user = call.userSession?.user ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val question = serverState.questions[id] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                if (createdComment.content.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val prediction = serverState.getUserPredictions(user)[id]?.takeIf {
                    createdComment.attachPrediction
                }

                val comment = Comment(user, createdComment.timestamp, createdComment.content, prediction)
                if (serverState.comments.get(question)?.add(comment) == true) {
                    call.transientUserData?.refreshRunningWebsockets()
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
            post("/send_prediction/{id}") {
                val dist: ProbabilityDistribution = call.receive()
                val id = call.parameters["id"] ?: ""
                val user = call.userSession?.user ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                   return@post
                }

                val question = serverState.questions[id] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                if (question.answerSpace != dist.space) {
                    print("Prediction not compatible with answer space")
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val pred = Prediction(unixNow(), dist)
                serverState.addPrediction(question, pred)

                call.transientUserData?.refreshRunningWebsockets()
                call.respond(HttpStatusCode.OK)
            }
            webSocket("/state") {
                // Require a session to already be initialized; it is not possible
                // to edit session cookies within websockets.
                val session = call.userSession

                if (session == null) {
                    // Code 3000 is registered with IANA as "Unauthorized".
                    close(CloseReason(3000, "Missing session"))
                    return@webSocket
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

                    val state = serverState.export(sessionData)
                    send(Frame.Text(confidoJSON.encodeToString(state)))
                }
            }
            val staticDir = File(System.getenv("CONFIDO_STATIC_PATH") ?: "./build/distributions/").canonicalFile
            println("static dir: $staticDir")
            static("/static") {
                staticRootFolder = staticDir
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP) {
                    files(".")
                }
            }
            webSocket("/state_presenter") {
                val session = call.userSession

                if (session == null) {
                    // Code 3000 is registered with IANA as "Unauthorized".
                    close(CloseReason(3000, "Missing session"))
                    return@webSocket
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