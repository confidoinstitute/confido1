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
import react.dom.html.ReactHTML.h1
import react.dom.onChange
import react.router.Route
import react.router.Routes
import react.router.dom.BrowserRouter
import tools.confido.payloads.SetName
import tools.confido.question.*
import tools.confido.state.AppState

val AppStateContext = createContext<AppState>()

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
            +"From state: your name is ${appState.session.name ?: "not set"} and language is ${appState.session.language}."
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
                    Client.postData("/setName", SetName(name))
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
            @Suppress("RedundantUnitExpression")
            Unit // This is not redundant, because assignment fails some weird type checks
        }
        ws.onclose = {
            appState = null
            @Suppress("RedundantUnitExpression")
            Unit // This is not redundant, because assignment fails some weird type checks
        }
        cleanup {
            ws.close()
        }
    }

    CssBaseline {}
    AppBar {
        position = AppBarPosition.static
        Toolbar {
            Typography {
                +"Confido"
            }
        }
    }

    if (appState == null) {
//        Backdrop {
//            this.open = true
//            this.sx { this.zIndex = 42.asDynamic() }
//            CircularProgress {}
//        }
        return@FC
    }

    AppStateContext.Provider {
        value = appState ?: error("No app state!")

        if (appState?.session?.name == null) {
          h1 {
              +"Please, set your name."
          }
          SetNameForm {}
          return@Provider
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

                    this.element = GroupPredictions.create {
                        questions = null
                    }
                }
                Route {
                    path = "/set_name"

                    this.element = SetNameForm.create()
                }
                Route {
                    path = "/edit_questions"

                    this.element = EditQuestions.create()
                }
            }
        }
    }
}