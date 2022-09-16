package components

import csstype.AlignItems
import csstype.Display
import csstype.px
import emotion.react.css
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import org.w3c.dom.WebSocket
import react.*
import react.dom.html.ReactHTML.div
import react.dom.onChange
import react.router.Route
import react.router.Routes
import react.router.dom.BrowserRouter
import tools.confido.payloads.SetName
import tools.confido.question.*
import tools.confido.state.AppState
import kotlin.coroutines.EmptyCoroutineContext

val AppStateContext = createContext<AppState?>(null)

val SetNameForm = FC<Props> {
    val appState = useContext(AppStateContext)
    var name by useState<String>("")

    Paper {
        sx {
            marginTop = 10.px
            padding = 10.px
        }
        Typography {
            variant = TypographyVariant.body1
            +"From state: your name is ${appState?.session?.name ?: "not set"} and language is ${appState?.session?.language ?: "not set"}."
        }
        div {
            css {
                marginTop = 5.px
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
}

val App = FC<Props> {
    var appState by useState<AppState?>(null)
    val webSocket = useRef<WebSocket>(null)

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

    AppStateContext.Provider {
        value = appState

        AppBar {
            position = AppBarPosition.static
            Toolbar {
                Typography {
                    +"Confido"
                }
            }
        }

        BrowserRouter {
            Navigation {}
            Routes {
                Route {
                    index = true
                    path = "/"
                    this.element = QuestionList.create()
                }
                Route {
                    path = "/group_predictions"

                    val groupPredictions = listOf(
                        Question("staticNumeric", "What are you predictions?", true, NumericAnswerSpace(3, 0.0, 1.0))
                                to listOf(0.2, 0.5, 0.3),
                        Question("staticBinary", "Will we manage to finish Confido on time?", true, BinaryAnswerSpace())
                                to listOf(0.40, 0.60),
                        Question("dutchBinary", "Will Dutch government choose our app?", true, BinaryAnswerSpace())
                                to listOf(0.5, 0.5),
                    )
                    this.element = GroupPredictions.create {
                        predictions = groupPredictions
                    }
                }
                Route {
                    path = "/set_name"

                    this.element = SetNameForm.create()
                }
            }
        }
    }
}