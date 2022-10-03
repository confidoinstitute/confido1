package tools.confido.application

import io.ktor.http.HttpStatusCode
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
import kotlinx.datetime.LocalDate
import kotlinx.html.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import payloads.CreatedComment
import tools.confido.application.ServerState.questions
import tools.confido.application.sessions.*
import tools.confido.payloads.*
import tools.confido.question.Question
import tools.confido.state.AppState
import tools.confido.state.UserSession
import tools.confido.question.*
import java.io.File

fun HTML.index() {
    head {
        title("Confido")
    }
    body {
        script(src = "/static/confido1.js") {}
    }
}

object ServerState {
    var questions: Map<String, Question> = emptyMap()
    val userPredictions: MutableMap<String, MutableMap<String, Prediction>> = mutableMapOf()
    var groupDistributions: MutableMap<String, List<Double>> = mutableMapOf()
    var comments: MutableMap<String, MutableList<Comment>> = mutableMapOf()

    fun loadQuestions(collection: CoroutineCollection<Question>) {
        questions = runBlocking {
            collection.find().toList().associateBy { question -> question.id }
        }
        // TODO actually store comments!
        comments = questions.mapValues { mutableListOf<Comment>() }.toMutableMap()
        calculateGroupDistribution()
    }
    fun calculateGroupDistribution(question: Question) {
        groupDistributions[question.id] = userPredictions.values.mapNotNull {
            it[question.id]?.let { prediction -> question.answerSpace.predictionToDistribution(prediction) }
        }.fold(List(question.answerSpace.bins) { 0.0 }) { acc, dist ->
            dist.zip(acc) { a, b -> a + b }
        }
    }
    fun calculateGroupDistribution() {
        questions.mapValues { (_, question) -> calculateGroupDistribution(question) }
    }
    fun appState(sessionData: UserSession) =
    AppState(
        questions,
        userPredictions[sessionData.name]?.toMap() ?: emptyMap(),
        comments,
        groupDistributions.toMap(),
        sessionData,
        sessionData.name == "Admin")
}

fun main() {
    val client = KMongo.createClient().coroutine
    val database = client.getDatabase("confido1")
    val questionCollection = database.getCollection<Question>("question")

    ServerState.loadQuestions(questionCollection)

    embeddedServer(CIO, port = 8080, host = "127.0.0.1") {
        install(WebSockets)
        install(CallLogging)
        install(ContentNegotiation) {
            this.json(Json)
        }
        install(Sessions)
        routing {
            get("/init") {
                // TODO: Secure this or replace.
                val commonAnswerSpace = NumericAnswerSpace(32, 0.0, 50.0)
                val questions = listOf(
                    Question("question1", "How are you?", enabled = false, answerSpace = NumericAnswerSpace(32, 0.0, 50.0)),
                    Question("numeric_big", "What big number do you like", answerSpace = NumericAnswerSpace(32, 1.0, 7280.0)),
                    Question("numeric_date", "When will this happen?", predictionsVisible = true, resolved = true, answerSpace = NumericAnswerSpace.fromDates(LocalDate(2022,1,1), LocalDate(2022,12,31))),
                    Question("question2", "Is this good?", predictionsVisible = true, answerSpace = BinaryAnswerSpace()),
                    Question(
                        "invisible_question",
                        "Can you not see this?",
                        visible = false,
                        answerSpace = commonAnswerSpace
                    ),
                )
                questionCollection.drop()
                questionCollection.insertMany(questions)
                ServerState.loadQuestions(questionCollection)
            }
            get("/{...}") {
                if (call.userSession == null) {
                    call.userSession = UserSession(name = null, language = "en")
                }

                call.respondHtml(HttpStatusCode.OK, HTML::index)
            }
            post("/setName") {
                val session = call.userSession
                if (session == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    val setName: SetName = Json.decodeFromString(call.receiveText())
                    call.userSession = session.copy(name = setName.name)
                    if (!ServerState.userPredictions.containsKey(setName.name))
                        ServerState.userPredictions[setName.name] = mutableMapOf()

                    call.transientUserData?.refreshRunningWebsockets()
                    call.respond(HttpStatusCode.OK)
                }
            }
            editQuestion(this)
            post("/add_comment/{id}") {
                val createdComment: CreatedComment = call.receive()
                val id = call.parameters["id"] ?: ""
                val userName = call.userSession?.name

                val question = questions[id]
                if (question == null || userName == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                if (createdComment.content.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val prediction = ServerState.userPredictions[userName]?.get(id)?.takeIf {
                    createdComment.attachPrediction
                }

                val comment = Comment(userName, createdComment.timestamp, createdComment.content, prediction)
                if (ServerState.comments[id]?.add(comment) == true) {
                    call.transientUserData?.refreshRunningWebsockets()
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
            post("/send_prediction/{id}") {
                val prediction: Prediction = call.receive()
                val id = call.parameters["id"] ?: ""
                val userName = call.userSession?.name

                val question = questions[id]
                if (question == null || userName == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                if (!question.answerSpace.verifyPrediction(prediction)) {
                    print("Prediction not compatible with answer space")
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                ServerState.userPredictions[userName]?.set(id, prediction)
                ServerState.calculateGroupDistribution(question)

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

                    val state = ServerState.appState(sessionData)
                    send(Frame.Text(Json.encodeToString(state)))
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
        }
    }.start(wait = true)
}