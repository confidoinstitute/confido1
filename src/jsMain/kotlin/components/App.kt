package components

import csstype.AlignItems
import csstype.Display
import csstype.px
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mui.material.*
import mui.system.sx
import org.w3c.dom.WebSocket
import react.*
import react.dom.onChange
import tools.confido.payloads.SetName
import tools.confido.state.AppState
import kotlin.coroutines.EmptyCoroutineContext

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

    Paper {
        sx {
            marginTop = 10.px
            padding = 10.px
            display = Display.flex
            alignItems = AlignItems.flexEnd
        }
        TextField {
            variant = FormControlVariant.standard
            id = "name-field"
            label = ReactNode("Name")
            value = name
            onChange = {
                name = it.asDynamic().target.value as String
            }
        }
        Button {
            onClick = {
                // TODO: Persist this client and reuse for all requests
                val client = HttpClient {
                    install(ContentNegotiation) {
                        json()
                    }
                }

                // Not sure if this is the best way to do this.
                CoroutineScope(EmptyCoroutineContext).launch {
                    client.post("setName") {
                        contentType(ContentType.Application.Json.withParameter("charset", "utf-8"))
                        setBody(SetName(name))
                    }
                }
            }
            +"Set name"
        }
    }
}