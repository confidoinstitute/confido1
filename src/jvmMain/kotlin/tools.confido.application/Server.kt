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
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import kotlinx.datetime.Clock.System.now
import kotlinx.html.*
import kotlinx.serialization.encodeToString
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_224
import org.litote.kmongo.serialization.registerModule
import org.litote.kmongo.serialization.registerSerializer
import org.simplejavamail.mailer.MailerBuilder
import rooms.*
import tools.confido.application.sessions.*
import tools.confido.refs.*
import tools.confido.serialization.confidoJSON
import tools.confido.serialization.confidoSM
import tools.confido.state.*
import users.*
import java.io.File
import java.time.Duration
import kotlin.collections.listOf

val staticDir = File(System.getenv("CONFIDO_STATIC_PATH") ?: "./build/distributions/").canonicalFile
val jsBundle = staticDir.resolve("confido1.js")
val jsHash = DigestUtils(SHA_224).digestAsHex(jsBundle)
val devMode = System.getenv("CONFIDO_DEVMODE") == "1"
val demoMode = System.getenv("CONFIDO_DEMO") == "1"

fun HTML.index() {
    head {
        title("Confido")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
    }
    body {
        script(type="text/javascript") { +"bundleVer= '${jsHash}'" }
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
suspend inline fun PipelineContext<Unit, ApplicationCall>.unauthorized(msg: String = "") {
    System.err.println("bad request: ${msg}")
    call.respond(HttpStatusCode.Unauthorized, msg)
}
suspend inline fun PipelineContext<Unit, ApplicationCall>.notFound(msg: String = "") {
    System.err.println("bad request: ${msg}")
    call.respond(HttpStatusCode.NotFound, msg)
}

// TODO move this to an external script for easier modification
suspend fun initDemo() {
    val admin = User(id="admin", type =UserType.ADMIN, email="admin@confido.example", emailVerified = true, nick = "Demo Admin")
    val user1 = User(id="user1", type =UserType.MEMBER, email="user1@confido.example", emailVerified = true, nick = "Demo User 1")
    val user2 = User(id="user2", type =UserType.MEMBER, email="user2@confido.example", emailVerified = true, nick = "Demo User 2")
    val users = listOf(
        admin,user1, user2
    )
    users.forEach {
        if (!serverState.userManager.byEmail.containsKey(it.email))
            serverState.userManager.insertEntity(it, forceId = true)
    }
    val rooms = listOf(
        Room(id="demoroom1", name="Testing room", members = listOf(
            RoomMembership(admin.ref, role = Moderator),
            RoomMembership(user1.ref, role = Forecaster),
            RoomMembership(user2.ref, role = Forecaster),
        ))
    )

    rooms.forEach {
        if (!serverState.roomManager.entityMap.containsKey(it.id))
            serverState.roomManager.insertEntity(it, forceId = true)
    }
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
        if (appConfig.demoMode) initDemo()
    }


    embeddedServer(CIO, port = System.getenv("CONFIDO_HTTP_PORT")?.toIntOrNull() ?: 8080, host = "127.0.0.1") {
        install(WebSockets)
        install(CallLogging)
        install(ContentNegotiation) {
            this.json(confidoJSON)
        }
        install(Sessions)
        install(Mailing) {
            // This is the URL of the hosted frontend used in mailed links (no trailing /)
            urlOrigin = (System.getenv("CONFIDO_BASE_URL") ?: "http://localhost:8081").trimEnd('/')
            debugMode = System.getenv("CONFIDO_MAIL_DEBUG") == "1"
            senderAddress = System.getenv("CONFIDO_MAIL_SENDER") ?: "noreply@confido.tools"
            mailer = MailerBuilder
                .withSMTPServer(System.getenv("CONFIDO_SMTP_HOST")?:"localhost",
                              System.getenv("CONFIDO_SMTP_PORT")?.toIntOrNull()?:25)
                .buildMailer()
        }
        routing {
            getST("/export.csv") {
                val user = call.userSession?.user ?: return@getST unauthorized("Not logged in.")

                val questions = call.parameters["questions"]?.split(",")?.mapNotNull { serverState.questions[it] } ?: emptyList()
                val rooms = questions.mapNotNull { serverState.questionRoom[it.ref]?.deref() }.toSet()
                if (questions.isEmpty()) return@getST badRequest("No question to be exported.")

                val canIndividual = rooms.all { it.hasPermission(user, RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS) }
                val canGroup = rooms.all { it.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS) }
                val group = call.parameters["group"]?.toBoolean()
                when(group) {
                    true -> if (!canGroup) return@getST unauthorized("You cannot export group predictions.")
                    false -> if (!canIndividual) return@getST unauthorized("You cannot export individual predictions.")
                    null -> return@getST badRequest("You must specify if you export group or individual predictions.")
                }

                val history = call.parameters["history"]?.let { ExportHistory.valueOf(it) } ?: ExportHistory.LAST
                val buckets = call.parameters["buckets"]?.toIntOrNull() ?: 32
                if (buckets < 0) return@getST badRequest("You cannot request negative amount of buckets.")

                val exporter = PredictionExport(questions, group, history, buckets)

                call.respondTextWriter(contentType = ContentType("text","csv")) {
                    exporter.exportCSV().collect {
                        write(it)
                        write("\r\n") // CSV specs says you must use CRLF.
                    }
                }
            }
            getST("/{...}") {
                if (call.userSession == null) {
                    call.setUserSession(UserSession())
                }

                call.respondHtml(HttpStatusCode.OK, HTML::index)
            }
            if (devMode) {
                getST("/init/") {
                    val userPasswordHash = Password.hash(DebugAdmin.password).addRandomSalt().withArgon2().result
                    serverState.userManager.insertEntity(
                        User(
                            "debugadmin",
                            UserType.ADMIN,
                            DebugAdmin.email,
                            true,
                            "debugadmin",
                            userPasswordHash,
                            now(),
                            now()
                        )
                    )
                    val memberPasswordHash = Password.hash(DebugMember.password).addRandomSalt().withArgon2().result
                    serverState.userManager.insertEntity(
                        User(
                            "debugmember",
                            UserType.MEMBER,
                            DebugMember.email,
                            true,
                            "debugmember",
                            memberPasswordHash,
                            now(),
                            now()
                        )
                    )
                    serverState.roomManager.insertEntity(
                        Room("testroom", "Testing room", Clock.System.now(), "This is a testing room.")
                    )
                }
            }
            loginRoutes(this)
            profileRoutes(this)
            inviteRoutes(this)
            adminUserRoutes(this)
            roomRoutes(this)
            roomCommentsRoutes(this)
            questionRoutes(this)
            questionCommentsRoutes(this)
            genericRoutes(this)

            webSocketST("/state") {
                print(call.request.headers["user-agent"])
                print(call.request.headers.toMap())
                val clientVer = call.request.queryParameters["bundleVer"] ?: ""
                if (clientVer.isNotEmpty() && clientVer != jsHash && (call.request.headers["x-webpack-dev"] ?: "").isEmpty()) {
                    System.err.println("Forcing reload - clientVer: $clientVer jsHash: $jsHash")
                    // If someone has the website open and an old frontend loaded and server then gets
                    // updated, we have to force reload of the page, otherwise, likely state deserialization
                    // would fail anyway.
                    // Later, if we add offline clients / mobile apps, we may have more relaxed version rules
                    // and explicit backward compatibility guarantees / protocol versioning / etc.
                    // Check is skipped when using webpack development proxy, because then hash of the final
                    // bundle is meaningless as the proxy serves its own javascript. (The header is added
                    // in webpack-config/config.js, it is not a standard webpack feature.)
                    close(CloseReason(4001, "Incompatible frontend version"))
                    return@webSocketST
                }
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
