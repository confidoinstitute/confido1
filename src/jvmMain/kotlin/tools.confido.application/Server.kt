package tools.confido.application

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        routing {
            get("/") {
                call.respondHtml(HttpStatusCode.OK, HTML::index)
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
                    print("Prediction not compatibile with answer space")
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                call.respond(HttpStatusCode.OK)
            }
            webSocket("/state") {
                    this.send(Json.encodeToString(questions))
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