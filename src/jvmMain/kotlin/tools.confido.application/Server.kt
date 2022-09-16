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
import kotlinx.html.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

val commonAnswerSpace = NumericAnswerSpace(32, 0.0, 50.0)
val questions = listOf(
    Question("question1", "How are you?", visible = true, answerSpace = commonAnswerSpace),
    Question("question2", "Is this good?", visible = true, answerSpace = BinaryAnswerSpace()),
    Question(
        "invisible_question",
        "Can you not see this?",
        visible = false,
        answerSpace = commonAnswerSpace
    ),
)

fun main() {
    embeddedServer(CIO, port = 8080, host = "127.0.0.1") {
        install(WebSockets)
        install(ContentNegotiation) {
            this.json()
        }
        install(Sessions)
        routing {
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
                    call.transientData?.websocketRefreshChannel?.send(Unit)

                    call.respond(HttpStatusCode.OK)
                }
            }
            post("/send_prediction/{id}") {
                val prediction: Prediction = call.receive()
                println(prediction)
                val id = call.parameters["id"] ?: ""

                val question = questions.firstOrNull { it.id == id }
                if (question == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                println(question.answerSpace)
                if (!question.answerSpace.verifyPrediction(prediction)) {
                    print("Prediction not compatible with answer space")
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
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

                while (true) {
                    val sessionData = call.userSession ?: continue
                    val state = AppState(questions, sessionData)
                    send(Frame.Text(Json.encodeToString(state)))
                    call.transientData?.websocketRefreshChannel?.receive() ?: break
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