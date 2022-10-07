package components

import components.rooms.Room
import components.rooms.RoomList
import csstype.*
import emotion.react.css
import icons.MenuIcon
import kotlinx.browser.window
import kotlinx.js.timers.setTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import org.w3c.dom.CloseEvent
import org.w3c.dom.WebSocket
import react.*
import react.dom.html.ReactHTML.div
import react.dom.onChange
import react.router.Route
import react.router.Routes
import react.router.dom.BrowserRouter
import tools.confido.payloads.SetName
import tools.confido.serialization.confidoJSON
import tools.confido.state.AppState
import utils.eventValue
import utils.webSocketUrl

val AppStateContext = createContext<ClientAppState>()

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
            +"From state: your name is ${appState.state.session.name ?: "not set"} and language is ${appState.state.session.language}."
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
                disabled = appState.stale
                onChange = {
                    name = it.eventValue()
                }
            }
            Button {
                onClick = {
                    Client.postData("/setName", SetName(name))
                }
                disabled = appState.stale
                +"Set name"
            }
        }
    }
}

data class ClientAppState(val state: AppState, val stale: Boolean = false)

val App = FC<Props> {
    var appState by useState<AppState?>(null)
    var stale by useState(false)
    val webSocket = useRef<WebSocket>(null)

    fun startWebSocket() {
        val ws = WebSocket(webSocketUrl("/state"))
        console.log("New websocket!")
        ws.apply {
            onmessage = {
                appState = confidoJSON.decodeFromString(it.data.toString())
                stale = false
                @Suppress("RedundantUnitExpression")
                Unit // This is not redundant, because assignment fails some weird type checks
            }
            onclose = {
                console.log("Closed websocket")
                stale = true
                webSocket.current = null
                (it as? CloseEvent)?.let {event ->
                    if (event.code == 3000.toShort())
                        window.location.reload()
                }
                setTimeout(::startWebSocket, 5000)
            }
        }
        webSocket.current = ws
    }

    useEffectOnce {
        startWebSocket()
        cleanup {
            // Do not push reconnect
            webSocket.current?.apply {
                onclose = null
                close()
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
        value = ClientAppState(appState ?: error("No app state!"), stale)

        if (appState?.session?.name == null) {
            RootAppBar {
                hasDrawer = false
            }
            Toolbar {}
          Typography {
              variant = TypographyVariant.h1
              +"Please, set your name."
          }
          SetNameForm {}
          return@Provider
        }

        BrowserRouter {
            RootLayout {}
        }
    }
}