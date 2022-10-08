package components

import components.rooms.InviteNewUserForm
import csstype.*
import kotlinx.browser.window
import kotlinx.js.timers.setTimeout
import kotlinx.serialization.decodeFromString
import mui.material.*
import org.w3c.dom.CloseEvent
import org.w3c.dom.WebSocket
import react.*
import react.router.Route
import react.router.Routes
import react.router.dom.BrowserRouter
import tools.confido.serialization.confidoJSON
import tools.confido.state.SentState
import utils.webSocketUrl

val AppStateContext = createContext<ClientAppState>()


data class ClientAppState(val state: SentState, val stale: Boolean = false)

val App = FC<Props> {
    var appState by useState<SentState?>(null)
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

        if (appState?.session?.user == null) {
            RootAppBar {
                hasDrawer = false
            }
            Toolbar {}
            BrowserRouter {
                Routes {
                    Route {
                        index = true
                        path = "/"
                        this.element = LandingPage.create()
                    }
                    Route {
                        path = "room/:roomID/invite/:inviteToken"
                        this.element = InviteNewUserForm.create()
                    }
                }
            }
        } else {
            BrowserRouter {
                RootLayout {}
            }
        }
    }
}