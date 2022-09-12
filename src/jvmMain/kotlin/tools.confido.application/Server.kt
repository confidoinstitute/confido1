package tools.confido.application

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.cio.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tools.confido.question.AnswerSpace
import tools.confido.question.BinaryAnswerSpace
import tools.confido.question.NumericAnswerSpace
import tools.confido.question.Question
import java.io.File

fun HTML.index() {
    head {
        title("Confido")
    }
    body {
        script(src = "/static/confido1.js") {}
    }
}

val channel = Channel<Unit>()

fun main() {
    embeddedServer(CIO, port = 8080, host = "127.0.0.1") {
        install(WebSockets)
        routing {
            get("/") {
                call.respondHtml(HttpStatusCode.OK, HTML::index)
            }
            webSocket("/state") {
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
                    println(Json.encodeToString(questions))

                    send(Frame.Text(Json.encodeToString(questions)))
                    delay(10000)
                    questions[2].visible = true
                    send(Frame.Text(Json.encodeToString(questions)))
                    delay(10000)
                    questions[0].visible = false
                    send(Frame.Text(Json.encodeToString(questions)))

                    channel.receive()
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