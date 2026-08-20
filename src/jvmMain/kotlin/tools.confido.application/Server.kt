package tools.confido.application

import com.password4j.Password
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.writeFully
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Clock.System.now
import kotlinx.html.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import tools.confido.payloads.responses.*
import org.slf4j.LoggerFactory
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_224
import org.litote.kmongo.serialization.registerModule
import org.litote.kmongo.serialization.registerSerializer
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.mailer.MailerBuilder
import tools.confido.rooms.*
import tools.confido.application.routes.*
import tools.confido.application.sessions.Sessions
import tools.confido.application.sessions.renewUserSession
import tools.confido.application.sessions.transientUserData
import tools.confido.application.sessions.user
import tools.confido.application.sessions.userSession
import tools.confido.store.*
import tools.confido.extensions.ServerExtension
import tools.confido.refs.RefAsStringSerializer
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.serialization.confidoJSON
import tools.confido.serialization.confidoSM
import tools.confido.state.QuestionCounts
import tools.confido.state.appConfig
import tools.confido.state.serverState
import tools.confido.state.Topics
import tools.confido.users.DebugAdmin
import tools.confido.users.DebugMember
import tools.confido.users.User
import tools.confido.users.UserType
import java.io.File
import kotlin.collections.set
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val staticDir = File(System.getenv("CONFIDO_STATIC_PATH") ?: "build/dist/js/productionExecutable").canonicalFile
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

/** Logger for work that runs outside of any request, where `call.application.log` is unavailable. */
val backgroundLog = LoggerFactory.getLogger("confido.background")!!
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
        if (it.email?.lowercase()?.let { email -> tools.confido.store.userStore.byEmail[email] } == null)
            tools.confido.store.userStore.insert(it)
    }
    val rooms = listOf(
        Room(id = "demoroom1", name = "Testing room", members = listOf(
            RoomMembership(admin.ref, role = appConfig.roleById("moderator") ?: error("Role 'moderator' not found")),
            RoomMembership(user1.ref, role = appConfig.roleById("forecaster") ?: error("Role 'forecaster' not found")),
            RoomMembership(user2.ref, role = appConfig.roleById("forecaster") ?: error("Role 'forecaster' not found")),
        ))
    )

    rooms.forEach {
        if (tools.confido.store.roomStore.get(it.id) == null)
            tools.confido.store.roomStore.insert(it)
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
                tools.confido.store.userStore.insert(user)
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
    registerSerializer(tools.confido.rooms.RoomRoleRefSerializer)

    // Registration order = boot load order: base entity stores first, then the stores
    // whose boot folds dereference them (prediction group aggregates need questions).
    tools.confido.store.registerRoomStores()
    tools.confido.store.registerQuestionStores()
    tools.confido.store.registerUserStores()
    tools.confido.store.registerSessionStores()
    tools.confido.store.registerLinkStores()
    tools.confido.store.registerPresentationStores()
    tools.confido.store.registerPredictionStores()
    tools.confido.store.registerCommentStores()

    runBlocking {
        serverState.initialize()
        tools.confido.application.migrations.MigrationRunner.runPending(serverState.database, serverState.collectionVerifiers())
        serverState.load()
        if (appConfig.demoMode) initDemo()
        else initData()
        serverState.extensions.forEach { it.serverInit() }

        launch(context= singleThreadContext) {
            val expiringStores = tools.confido.store.Stores.all.filterIsInstance<tools.confido.store.ExpiringStore>()

            while (true) {
                // These loops are children of the same scope, so an uncaught throw here
                // would cancel the sibling loop as well and both would stay dead until
                // the next restart, with the HTTP server still happily serving.
                try {
                    expiringStores.forEach {
                        it.cleanupExpired()
                    }
                } catch (e: Throwable) {
                    backgroundLog.error("Expired-entity cleanup failed", e)
                }
                delay(1.hours)
            }
        }

        // Must share the single thread with request handlers: this mutates the same
        // in-memory entity maps they read and write.
        launch(context = singleThreadContext) {
            while (true) {
                delay(if (appConfig.devMode) 5.seconds else 15.minutes)
                try {
                    checkAllQuestionSchedules()
                } catch (e: Throwable) {
                    backgroundLog.error("Question schedule check failed", e)
                }
            }
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
            installCsrfProtection()
            install(Mailing) {
                // This is the URL of the hosted frontend used in mailed links (no trailing /)
                urlOrigin = (System.getenv("CONFIDO_BASE_URL") ?: "http://localhost:8081").trimEnd('/')
                debugMode = System.getenv("CONFIDO_MAIL_DEBUG") == "1"
                senderAddress = System.getenv("CONFIDO_MAIL_SENDER") ?: "noreply@confido.tools"
                var builder = MailerBuilder
                    .withSMTPServer(
                        System.getenv("CONFIDO_SMTP_HOST") ?: "localhost",
                        System.getenv("CONFIDO_SMTP_PORT")?.toIntOrNull() ?: 25,
                        System.getenv("CONFIDO_SMTP_USER"),
                        System.getenv("CONFIDO_SMTP_PASS"),
                    )
                    .withTransportStrategy(
                        when (System.getenv("CONFIDO_SMTP_TRANSPORT") ?: "plain") {
                            "plain" -> TransportStrategy.SMTP
                            "tls" -> TransportStrategy.SMTPS
                            "starttls" -> TransportStrategy.SMTP_TLS
                            else -> throw Exception("Invalid CONFIDO_SMTP_TRANSPORT value")
                        }
                    )
                mailer = builder.buildMailer()
            }
            routing {
                getST("/export.csv") {
                    val user = call.userSession?.user ?: unauthorized("Not logged in.")
                    if (user.type == UserType.GUEST) unauthorized("Guests cannot make exports")

                    val questions = call.parameters["questions"]?.split(",")?.mapNotNull { serverState.questions[it] }
                        ?: emptyList()
                    val rooms = questions.mapNotNull { serverState.questionRoom[it.ref]?.deref() }.toSet()
                    if (questions.isEmpty()) badRequest("No question to be exported.")
                    // A question whose room cannot be resolved would silently drop out of
                    // `rooms`, and the all{} checks below are vacuously true on an empty set.
                    if (questions.any { serverState.questionRoom[it.ref]?.deref() == null })
                        notFound("No such question.")

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

                    call.respondBytesWriter(contentType = ContentType("text", "csv")) {
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
                    // Topic payload computations per topic pattern — makes the shared
                    // per-(topic, permission-class) views' compute-once observable
                    // (tests/state/shared_test.py).
                    getST("/diag/topic_computes") {
                        val stats = TopicStats.computes + mapOf(
                            "load:questionComments" to questionCommentArchive.partitions.loads,
                            "load:roomComments" to roomCommentArchive.partitions.loads,
                        )
                        call.respondText(
                            confidoJSON.encodeToString(stats),
                            ContentType.Application.Json
                        )
                    }
                    getST("/init/") {
                        val userPasswordHash = Password.hash(DebugAdmin.password).addRandomSalt().withArgon2().result
                        tools.confido.store.userStore.insert(
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
                        tools.confido.store.userStore.insert(
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
                        tools.confido.store.roomStore.insert(
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
                importQuestionRoutes(this)
                genericRoutes(this)
                presenterRoutes(this)
                presentationRoutes(this)
                calibrationRoutes(this)
                scoreRoutes(this)
                dashboardRoutes(this)
                searchRoutes(this)

                // The workspace-wide "questions" topic is retired (2026-08-20): the
                // dashboard reads the dashboard/<section> topics, search moved
                // server-side (POST /search), calibration detail carries display data
                // on its payload, and by-id consumers use question/<id> (useQuestion).

                // §4.2 users: the user directory, proper (admin/member) users only —
                // guests' identity needs are covered by presentations embedded in
                // payloads (§5.3), so the referencedUsers machinery is gone and the
                // wake set is just the user store. Subscribed on demand by the admin
                // directory and the add-member dialog, not root-subscribed.
                // Scope: the users censor reads only the user type, so all proper
                // users share one payload — the widest sharing in the system.
                sharedViewWS(Topics.users, scope = { cls -> cls.scopedTo(emptySet()) }) { cls ->
                    userStore.state.asView().map {
                        if (cls.userType !in setOf(UserType.ADMIN, UserType.MEMBER))
                            unauthorized("Cannot list users.")
                        ClassCensor(cls).censorUsers()
                    }
                }

                // §4.2 "rooms": the room directory — configuration + the viewer's own
                // membership + question refs, one map for all rooms the viewer can see.
                // Member lists, invite links and presentation state live on the per-room
                // detail topic below; this is what backs the client's global rooms map
                // (deref, room list, cross-room surfaces).
                viewWS(Topics.rooms) {
                    if (call.userSession?.user == null) unauthorized("Not logged in.")
                    combineViews(
                        roomStore.state.asView(),
                        questionStore.state.asView(),
                        userStore.state.asView(),
                    ).map {
                        val sessionData = call.userSession ?: unauthorized("Not logged in.")
                        if (sessionData.user == null) unauthorized("Not logged in.")
                        StateCensor(sessionData).censorRoomsDirectory()
                    }
                }

                // §4.2 "room/<id>": the full censored room — invite links, presentation
                // state, the viewer's own effective membership (member lists travel on
                // "room/<id>/members"). Component-scoped: the room page (and the
                // presenter's invite view) subscribe it on demand. The gate errors for
                // non-members and recovers on join (same subscription).
                viewWS(Topics.room) { rRef ->
                    if (call.userSession?.user == null) unauthorized("Not logged in.")
                    combineViews(
                        // Whole-map room view (not a cell): effective-membership extension
                        // hooks (room groups) read other rooms too.
                        roomStore.state.asView(),
                        questionStore.state.asView(),
                        userStore.state.asView(),
                        serverState.presentationRunStatesState.asView(),
                    ).map {
                        val sessionData = call.userSession ?: unauthorized("Not logged in.")
                        if (sessionData.user == null) unauthorized("Not logged in.")
                        val room = rRef.deref() ?: notFound("No such room.")
                        StateCensor(sessionData).censorRoom(room)
                            ?: unauthorized("You cannot view this room.")
                    }
                }

                // §5.3 "room/<id>/members": memberships as viewer-censored presentations
                // — never joinable user refs; userId/email are granted only to
                // MANAGE_MEMBERS viewers. Split from the detail topic so member data
                // reaches only the members tab / add-member dialog that subscribe it.
                viewWS(Topics.roomMembers) { rRef ->
                    if (call.userSession?.user == null) unauthorized("Not logged in.")
                    combineViews(
                        // Whole-map room view: effective-membership extension hooks
                        // (room groups) read other rooms; admin injection reads users.
                        roomStore.state.asView(),
                        userStore.state.asView(),
                    ).map {
                        val sessionData = call.userSession ?: unauthorized("Not logged in.")
                        if (sessionData.user == null) unauthorized("Not logged in.")
                        val room = rRef.deref() ?: notFound("No such room.")
                        StateCensor(sessionData).censorRoomMembers(room)
                            ?: unauthorized("You cannot view this room.")
                    }
                }

                // §4.2 "room/<id>/questions": the room page's question slice — own +
                // effective questions, entities only (counts on the sibling below).
                // Wake set is entity stores alone, so prediction/comment churn never
                // re-censors or re-sends the collection.
                sharedViewWS(Topics.roomQuestions, scope = ::roomQuestionsTopicScope) { cls, rRef ->
                    combineViews(
                        questionStore.state.asView(),
                        roomStore.state.asView(),
                        userStore.state.asView(),
                    ).map {
                        val room = rRef.deref() ?: notFound("No such room.")
                        ClassCensor(cls).censorRoomQuestions(room)
                            ?: unauthorized("You cannot view this room.")
                    }
                }

                // "room/<id>/questions/counts": the room slice's counts — the
                // question/<id> vs question/<id>/counts cutoff applied at collection
                // scale. Wakes on the count folds; the entity stores are gate inputs
                // (visibility filtering), with the equality cutoff suppressing frames
                // when an entity edit recomputes the same counts.
                sharedViewWS(Topics.roomQuestionCounts, scope = ::roomQuestionsTopicScope) { cls, rRef ->
                    combineViews(
                        questionStore.state.asView(),
                        roomStore.state.asView(),
                        userStore.state.asView(),
                        predictionData.predictionCount.state.asView(),
                        predictionData.predictorCount.state.asView(),
                        commentData.commentCount.state.asView(),
                    ).map {
                        val room = rRef.deref() ?: notFound("No such room.")
                        ClassCensor(cls).censorRoomQuestionCounts(room)
                            ?: unauthorized("You cannot view this room.")
                    }
                }

                // "question/<id>": one censored question — the by-id access behind the
                // client's useQuestion (presenter surfaces and describers hold bare
                // refs with no room context). Entity data only; counts ride on the
                // collection topics, so count churn never frames this one.
                sharedViewWS(Topics.question, scope = ::questionTopicScope) { cls, qRef ->
                    combineViews(
                        questionStore.state.asView(),
                        roomStore.state.asView(),
                        userStore.state.asView(),
                    ).map {
                        val question = qRef.deref() ?: notFound("No such question.")
                        ClassCensor(cls).censorQuestion(question)
                            ?: unauthorized("You cannot view this question.")
                    }
                }

                // "question/<id>/counts": the per-question counts for bare-ref surfaces
                // (presenter tiles' "N people answered"). A sibling of the entity topic
                // rather than a field of it, so count churn (every prediction/comment)
                // and entity churn never frame each other. Same visibility gate.
                sharedViewWS(Topics.questionCounts, scope = ::questionTopicScope) { cls, qRef ->
                    combineViews(
                        questionStore.state.asView(),
                        roomStore.state.asView(),
                        userStore.state.asView(),
                        predictionData.predictionCount.state.asView(),
                        predictionData.predictorCount.state.asView(),
                        commentData.commentCount.state.asView(),
                    ).map {
                        val question = qRef.deref() ?: notFound("No such question.")
                        ClassCensor(cls).censorQuestion(question)
                            ?: unauthorized("You cannot view this question.")
                        QuestionCounts(
                            predictions = serverState.predictionCount[qRef] ?: 0,
                            predictors = serverState.predictorCount[qRef] ?: 0,
                            comments = serverState.commentCount[qRef] ?: 0,
                        )
                    }
                }

                // The per-user slice (§4.2 "self"): session (incl. presenterInfo, which
                // updates through the session cell), own predictions, password flag,
                // presenter-window state. Split from "/state" so per-user churn (session
                // renewals, presenting, predicting) never invalidates the big snapshot.
                viewWS(Topics.self) {
                    val transientData = call.transientUserData
                    if (call.userSession?.user == null || transientData == null) unauthorized("Not logged in.")
                    combineViews(
                        userSessionStore.state.asView().at(call.userSession!!.id),
                        userStore.state.asView(),
                        latestPredictions.state.asView(),
                        transientData.activePresenterWindowsState.asView(),
                    ).map {
                        val sessionData = call.userSession ?: unauthorized("Not logged in.")
                        if (sessionData.user == null) unauthorized("Not logged in.")
                        selfState(sessionData, transientData)
                    }
                }

                // The single multiplexed state websocket: carries all topics registered
                // via viewWS/flowWS (PERSISTENCE_SYNC_REDESIGN.md §4.1).
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
                    if (call.userSession?.user == null) {
                        // Code 3000 is registered with IANA as "Unauthorized".
                        close(CloseReason(3000, "Missing session or user"))
                        return@webSocketST
                    }
                    val transientData = call.transientUserData ?: run {
                        close(CloseReason(3000, "Missing session or user"))
                        return@webSocketST
                    }

                    timeoutMillis = 30_000
                    pingIntervalMillis = 15_000

                    class ActiveSub {
                        var job: Job? = null                 // changes-watcher (ViewTopic) / collector (FlowTopic)
                        var view: View<JsonElement>? = null  // present for ViewTopic subscriptions
                        var sentAt = -1L                     // TxClock serial as of the last sent payload
                        var broken = false                   // internal error: dead until resubscribed
                        var resources: SubscriptionResources? = null  // released on unsub/disconnect
                    }
                    val subs = mutableMapOf<String, ActiveSub>()

                    // Per-connection outbox with flush-time validation (§3.5). Wake signals
                    // carry no data — they only schedule a flush. At flush, the sender
                    // *validates* every subscribed ViewTopic in one non-suspending pass: by
                    // the View consistency contract all payloads of a frame observe the same
                    // transactional snapshot, no matter how raggedly the wake signals arrived
                    // (strict cross-topic atomicity), while the versioned-memo cutoffs reduce
                    // an unaffected topic's cost to a chain of Long comparisons — no
                    // recompute, no encode. The per-topic diff against the last payload sent
                    // remains as a backstop (and serves FlowTopics). FlowTopic payloads
                    // (suspending fetches, timed streams) are computed at emission time and
                    // staged; they sit outside the consistency pass. A slow client never
                    // blocks producers or queues unboundedly — a flush just covers more
                    // accumulated change.
                    val staged = LinkedHashMap<String, JsonElement>()
                    val lastSent = mutableMapOf<String, JsonElement>()
                    val flushSignal = Channel<Unit>(Channel.CONFLATED)
                    fun poke() { flushSignal.trySend(Unit) }
                    fun stage(topic: String, data: JsonElement) {
                        staged[topic] = data
                        poke()
                    }

                    fun errorElement(errType: WSErrorType, message: String) =
                        confidoJSON.encodeToJsonElement(WSError<Unit>(errType, message) as WSResponse<Unit>)

                    val senderJob = launch(singleThreadContext) {
                        for (signal in flushSignal) {
                            val updates = mutableListOf<TopicUpdate>()
                            fun offer(topic: String, data: JsonElement) {
                                if (lastSent[topic] != data) {
                                    updates.add(TopicUpdate(topic, data))
                                    lastSent[topic] = data
                                }
                            }
                            staged.forEach { (topic, data) -> offer(topic, data) }
                            staged.clear()
                            subs.forEach { (topic, sub) ->
                                val view = sub.view
                                if (view == null || sub.broken) return@forEach
                                try {
                                    if (view.validate() <= sub.sentAt) return@forEach
                                    sub.sentAt = TxClock.serial
                                    offer(topic, view.value)  // memoized — validate just computed it
                                } catch (e: Exception) {
                                    // ResponseErrors are already encoded as WSError payloads
                                    // by viewWS; anything reaching here is a bug in the topic —
                                    // disable it for this subscription rather than log-spamming
                                    // on every flush.
                                    call.application.log.error("Error computing topic $topic", e)
                                    sub.broken = true
                                    offer(topic, errorElement(WSErrorType.INTERNAL_ERROR, "Internal error"))
                                }
                            }
                            if (updates.isNotEmpty())
                                send(Frame.Text(confidoJSON.encodeToString(TopicFrame(updates))))
                        }
                    }

                    fun parseTopic(topic: String): Triple<Topic, List<Any>, Parameters>? {
                        val qIdx = topic.indexOf('?')
                        val path = if (qIdx >= 0) topic.substring(0, qIdx) else topic
                        val query = if (qIdx >= 0) parseQueryString(topic.substring(qIdx + 1)) else Parameters.Empty
                        val (handler, args) = TopicRegistry.match(path) ?: return null
                        return Triple(handler, args, query)
                    }

                    // Explicit context everywhere below: `launch` here binds to the websocket
                    // session's scope, which does not inherit the handler's single-threaded one.
                    suspend fun subscribe(topic: String) {
                        if (topic in subs) return
                        val (handler, args, query) = parseTopic(topic) ?: run {
                            stage(topic, errorElement(WSErrorType.NOT_FOUND, "No such topic"))
                            return
                        }
                        val sub = ActiveSub()
                        subs[topic] = sub
                        val body = WSBody(TopicCall(call, query))
                        sub.resources = body.resources
                        when (handler) {
                            is ViewTopic -> {
                                try {
                                    val view = handler.build(body, args)
                                    sub.view = view
                                    sub.job = launch(singleThreadContext) {
                                        view.changes.collect { poke() }
                                    }
                                    poke()  // initial payload computed at the next flush
                                } catch (e: ResponseError) {
                                    // Build-time error (e.g. unparseable parameters): single
                                    // error payload, subscription stays dead until resub.
                                    body.resources.closeAll()
                                    stage(topic, errorElement(e.wsCode, e.message))
                                } catch (e: Exception) {
                                    body.resources.closeAll()
                                    call.application.log.error("Error building topic $topic", e)
                                    stage(topic, errorElement(WSErrorType.INTERNAL_ERROR, "Internal error"))
                                }
                            }
                            is FlowTopic -> sub.job = launch(singleThreadContext) {
                                try {
                                    // stage() never suspends, so a blocked send cannot
                                    // back-pressure the collector; the outbox coalesces.
                                    handler.flow(body, args)
                                        .collect { element -> stage(topic, element) }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    // A broken topic must not take down the whole connection.
                                    call.application.log.error("Error collecting topic $topic", e)
                                    stage(topic, errorElement(WSErrorType.INTERNAL_ERROR, "Internal error"))
                                }
                            }
                        }
                    }

                    // Close the socket when the session dies (logout elsewhere, deletion,
                    // expiry): the retired global-refresh sweep used to perform this check
                    // on every tick; now it watches the session cell directly.
                    val sessionId = call.userSession?.id
                    val sessionWatch = launch(singleThreadContext) {
                        if (sessionId == null) return@launch
                        userSessionStore.state.map { it[sessionId] }.distinctUntilChanged().collect { s ->
                            if (s == null || s.isExpired() || s.userRef == null)
                                close(CloseReason(3000, "Session expired"))
                        }
                    }
                    val renewJob = launch(singleThreadContext) {
                        while (true) {
                            call.userSession?.let { call.renewUserSession() }
                            delay(1.hours)
                        }
                    }

                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val cmd = try {
                                confidoJSON.decodeFromString<TopicCommand>(frame.readText())
                            } catch (e: Exception) {
                                continue
                            }
                            // Subscriptions batched in one command start together, so their
                            // collectors' first (non-suspending) computations all run before
                            // the sender coroutine flushes — the initial payloads of a
                            // page's topic set arrive as one consistent frame.
                            cmd.sub.forEach { subscribe(it) }
                            cmd.unsub.forEach { topic ->
                                subs.remove(topic)?.let { it.job?.cancel(); it.resources?.closeAll() }
                                staged.remove(topic)  // don't deliver to a topic just unsubscribed
                                lastSent.remove(topic)
                            }
                        }
                    } finally {
                        sessionWatch.cancel()
                        renewJob.cancel()
                        senderJob.cancel()
                        // Cancel stream jobs so their try/finally cleanups run; release
                        // subscription resources (partition pins).
                        subs.values.forEach { it.job?.cancel(); it.resources?.closeAll() }
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

                ServerExtension.forEach { it.initRoutes(this) }

            }
        }.start(wait = true)
    }
}
