package components
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinext.js.getOwnPropertyNames
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.w3c.dom.WebSocket
import react.*
import react.dom.html.InputType
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input;
import tools.confido.payloads.SetName
import tools.confido.question.Question
import tools.confido.state.AppState

val App = FC<Props> {
    var appState by useState<AppState?>(null)
    val webSocket = useRef<WebSocket>(null)
    var name by useState<String>("")

    useEffectOnce {
        webSocket.current = WebSocket("ws://localhost:8080/state")
        val ws = webSocket.current ?: error("WebSocket does not exist???")
        ws.onmessage = {
            appState = Json.decodeFromString(it.data.toString())
            Unit // This is not redundant, because assignment fails some weird type checks
        }
        cleanup {
            ws.close()
        }
    }

    QuestionList {
        this.questions = appState?.questions ?: listOf()
    }

    div {
        +"Name"
        input {
            type = InputType.text
            value = name
            onChange = { event ->
                name = event.target.value
            }
        }
        button {
            onClick = {
                // TODO: solve scope handling (this is not the way to do it)
                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch {
                    // TODO: persist the client and make it available to all
                    val client = HttpClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    client.post("setName") {
                        contentType(ContentType.Application.Json.withParameter("charset", "utf-8"))
                        setBody(SetName(name))
                    }
                }
            }
            + "Set name"
        }
    }
}