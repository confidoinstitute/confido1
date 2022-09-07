package tools.confido.application

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.cio.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tools.confido.payloads.SetName
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

fun main() {
    embeddedServer(CIO, port = 8080, host = "127.0.0.1") {
        install(WebSockets)
        install(Sessions) {
            // TODO: get key from a reasonable place
            val secretSignKey = hex("9dc80be9c980c296536ac7c00dfed4eb")
            cookie<UserSession>("session") {
                transform(SessionTransportTransformerMessageAuthentication(secretSignKey))
            }
        }
        routing {
            get("/") {
                if (call.sessions.get<UserSession>() == null) {
                    call.sessions.set(UserSession(name = null, lang = "en"))
                }
                call.respondHtml(HttpStatusCode.OK, HTML::index)
            }
            post("/setName") {
                // TODO: discards other settings
                val setName: SetName = Json.decodeFromString(call.receiveText())
                call.sessions.set(UserSession(name = setName.name, lang = "en"))
                call.respond(HttpStatusCode.OK)
            }
            webSocket("/state") {
                val session = call.sessions.get<UserSession>()

                val questions = listOf(
                    Question("question_session","Is your name ${session?.name ?: "unnamed"}?", visible = true),
                    Question("question1","How are you?", visible = true),
                    Question("question2","Is this good?", visible = true),
                    Question("invisible_question","Can you not see this?", visible = false),
                )

                send(Frame.Text(Json.encodeToString(questions)))
                delay(10000)
                questions[3].visible = true
                send(Frame.Text(Json.encodeToString(questions)))
                delay(10000)
                questions[1].visible = false
                send(Frame.Text(Json.encodeToString(questions)))
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