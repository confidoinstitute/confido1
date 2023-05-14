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
import kotlinx.datetime.Clock
import kotlinx.datetime.Clock.System.now
import kotlinx.html.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_224
import org.litote.kmongo.serialization.registerModule
import org.litote.kmongo.serialization.registerSerializer
import org.simplejavamail.mailer.MailerBuilder
import rooms.*
import tools.confido.application.routes.*
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
val iconDir = System.getenv("CONFIDO_ICONS_PATH")?.let { File(it).canonicalFile }
val jsBundle = staticDir.resolve("confido1.js")
val jsHash = DigestUtils(SHA_224).digestAsHex(jsBundle)
val appConfigHash = DigestUtils(SHA_224).digestAsHex(Json.encodeToString(appConfig))
val devMode = System.getenv("CONFIDO_DEVMODE") == "1"
val demoMode = System.getenv("CONFIDO_DEMO") == "1"

fun HTML.index() {
    head {
        title("Confido")
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0,user-scalable=no,maximum-scale=1.0")
        link(rel = "icon", href = "/static/favicon.ico") { sizes = "any" }
        link(rel = "icon", href = "/static/icon.svg", type = "image/svg+xml")
        link(rel = "apple-touch-icon", href = "/static/apple-touch-icon.png")
        link(rel = "manifest", href = "static/manifest.webmanifest")
    }
    body {
        script {
            attributes["crossorigin"] = ""
            // TODO make configurable via environment
            src = "https://polyfill.x.confido.tools/api/polyfill?features=pointer-event,resize-observer"
        }
        script(type="text/javascript") { unsafe { +"bundleVer= '${jsHash}'" } }
        script(type="text/javascript") { unsafe { +"appConfigVer= '${appConfigHash}'" } }
        script(type="text/javascript") { unsafe { +"appConfig= ${Json.encodeToString(appConfig)}" } }
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
@OptIn(DelicateCoroutinesApi::class)
val singleThreadContext = newSingleThreadContext("confido_server")
// val singleThreadContext = IO.limitedParallelism(1) // alternative solution



// TODO move this to an external script for easier modification
suspend fun initDemo() {
    val admin = User(id="admin", type = UserType.ADMIN, email="admin@confido.example", emailVerified = true, nick = "Demo Admin")
    val user1 = User(id="user1", type = UserType.MEMBER, email="user1@confido.example", emailVerified = true, nick = "Demo User 1")
    val user2 = User(id="user2", type = UserType.MEMBER, email="user2@confido.example", emailVerified = true, nick = "Demo User 2")
    val users = listOf(
        admin,user1, user2
    )
    users.forEach {
        if (!serverState.userManager.byEmail.containsKey(it.email))
            serverState.userManager.insertEntity(it, forceId = true)
    }
    val rooms = listOf(
        Room(id = "demoroom1", name = "Testing room", members = listOf(
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

suspend fun initData() {
    // upon first start, create admin accounts specified in environment
    // in order to make first login after deploy possible
    if (serverState.users.isEmpty()) {
        val admins = System.getenv("CONFIDO_ADMIN_EMAILS")
        if (admins != null && admins.trim() != "") {
            admins.split(',').forEach {
                val user = User(email = it.trim(), emailVerified = true, createdAt = now(), type = UserType.ADMIN)
                serverState.userManager.insertEntity(user)
            }
        }
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
        else initData()
    }

    val port = System.getenv("CONFIDO_HTTP_PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("CONFIDO_HTTP_HOST") ?: "127.0.0.1"

    embeddedServer(CIO, port = port, host = host) {
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
                val user = call.userSession?.user ?: unauthorized("Not logged in.")
                if (user.type == UserType.GUEST) unauthorized("Guests cannot make exports")

                val questions = call.parameters["questions"]?.split(",")?.mapNotNull { serverState.questions[it] } ?: emptyList()
                val rooms = questions.mapNotNull { serverState.questionRoom[it.ref]?.deref() }.toSet()
                if (questions.isEmpty()) badRequest("No question to be exported.")

                val canIndividual = rooms.all { it.hasPermission(user, RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS) }
                val canGroup = rooms.all { it.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS) }

                val exportWhat = call.parameters["what"] ?: "predictions"

                val exporter = when (exportWhat) {
                    "predictions" -> {
                        val group = call.parameters["group"]?.toBoolean()
                        when (group) {
                            true -> if (!canGroup) unauthorized("You cannot export group predictions.")
                            false -> if (!canIndividual) unauthorized("You cannot export individual predictions.")
                            null -> badRequest("You must specify if you export group or individual predictions.")
                        }

                        val history =
                            call.parameters["history"]?.let { ExportHistory.valueOf(it) } ?: ExportHistory.LAST
                        val buckets = call.parameters["buckets"]?.toIntOrNull() ?: 32
                        if (buckets < 0) badRequest("You cannot request negative amount of buckets.")

                        PredictionExport(questions, group, history, buckets)
                    }
                    "comments" -> {
                        if (!rooms.all { it.hasPermission(user, RoomPermission.VIEW_QUESTION_COMMENTS) })
                            unauthorized("No permission to view comments")
                        CommentExport(questions)
                    }
                    else -> badRequest("Invalid export type $exportWhat")
                }

                call.respondBytesWriter(contentType = ContentType("text","csv")) {
                    exporter.exportCSV().collect {
                        val arr = it.encodeToByteArray()
                        writeFully(arr, 0, arr.size)
                        writeFully("\r\n".encodeToByteArray(), 0, 2) // CSV specs says you must use CRLF.
                    }
                }
            }
            getST("/{...}") {
                serveFrontend()
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
            roomQuestionRoutes(this)
            roomCommentsRoutes(this)
            questionRoutes(this)
            questionCommentsRoutes(this)
            genericRoutes(this)
            presenterRoutes(this)

            webSocketST("/state") {
                val clientVer = call.request.queryParameters["bundleVer"] ?: ""
                val appConfigVer = call.request.queryParameters["appConfigVer"] ?: ""
                val isDev = call.request.headers["x-webpack-dev"]?.isNotEmpty() ?: false

                if (clientVer.isNotEmpty() && clientVer != jsHash && !isDev) {
                    call.application.log.info("Forcing reload due to version mismatch - clientVer: $clientVer jsHash: $jsHash")
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

                if (appConfigVer.isNotEmpty() && appConfigVer != appConfigHash) {
                    call.application.log.info("Forcing reload due to app config mismatch - appConfigVer: $appConfigVer appConfigHash: $appConfigHash")
                    close(CloseReason(4001, "Incompatible app configuration"))
                    return@webSocketST
                }

                // Require a session to already be initialized; it is not possible
                // to edit session cookies within websockets.
                // In addition, require the user to be logged in.
                val user = call.userSession?.user

                if (user == null) {
                    // Code 3000 is registered with IANA as "Unauthorized".
                    close(CloseReason(3000, "Missing session or user"))
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
                    val transientData = call.transientUserData
                    if (sessionData?.user == null || transientData == null) {
                        closeNotifier.emit(true)
                        return@runRefreshable
                    }

                    val state = StateCensor(sessionData, transientData).censor()
                    send(Frame.Text(confidoJSON.encodeToString(state)))
                }
            }
            application.log.info("static dir: $staticDir")
            static("/static") {
                staticRootFolder = staticDir
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP) {
                    files(".")
                    resources()
                }
                static("/icons") {
                    iconDir?.let {
                        files(iconDir)
                    }
                }
            }
        }
    }.start(wait = true)
}
