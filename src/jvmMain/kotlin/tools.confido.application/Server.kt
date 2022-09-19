package tools.confido.application

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.cio.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.html.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo
import tools.confido.application.sessions.*
import tools.confido.payloads.SetName
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

fun main() {
    val client = KMongo.createClient().coroutine
    val database = client.getDatabase("confido1")
    val questionCollection = database.getCollection<Question>("question")

    val questions = runBlocking {
        questionCollection.find().toList().associate { question -> question.id to question }
    }

    val userPredictions: MutableMap<String, MutableMap<String, Prediction>> = mutableMapOf()

    embeddedServer(CIO, port = 8080, host = "127.0.0.1") {
        val application = this
        install(WebSockets)
        install(ContentNegotiation) {
            this.json(Json)
        }
        install(Sessions)
        routing {
            get("/init") {
                if (!application.developmentMode) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val commonAnswerSpace = NumericAnswerSpace(32, 0.0, 50.0)
                val questions = listOf(
                    Question("question1", "How are you?", visible = true, answerSpace = NumericAnswerSpace(32, 0.0, 50.0)),
                    Question("numeric_big", "What big number do you like", visible = true, answerSpace = NumericAnswerSpace(32, 1.0, 7280.0)),
                    Question("numeric_date", "When will this happen?", visible = true, answerSpace = NumericAnswerSpace.fromDates(LocalDate(2022,1,1), LocalDate(2022,12,31))),
                    Question("question2", "Is this good?", visible = true, answerSpace = BinaryAnswerSpace()),
                    Question(
                        "invisible_question",
                        "Can you not see this?",
                        visible = false,
                        answerSpace = commonAnswerSpace
                    ),
                )
                questionCollection.drop()
                questionCollection.insertMany(questions)
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
                    call.transientData?.websocketRefreshChannel?.update { !it }
                    if (!userPredictions.containsKey(setName.name))
                        userPredictions[setName.name] = mutableMapOf()

                    call.respond(HttpStatusCode.OK)
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
                println(question.answerSpace)
                if (!question.answerSpace.verifyPrediction(prediction)) {
                    print("Prediction not compatible with answer space")
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                userPredictions[userName]?.set(id, prediction)
                call.transientData?.websocketRefreshChannel?.update { !it }
                call.respond(HttpStatusCode.OK)
            }
            webSocket("/state") {
                // Require a session to already be initialized; it is not possible to edit sessions within websockets.
                val session = call.userSession

                if (session == null) {
                    // Code 3000 is registered with IANA as "Unauthorized".
                    close(CloseReason(3000, "Missing session"))
                    return@webSocket
                }

                call.transientData?.websocketRefreshChannel?.collect() {
                    val sessionData = call.userSession ?: return@collect
                    val state = AppState(questions, userPredictions[sessionData.name]?.toMap() ?: emptyMap(), sessionData)
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