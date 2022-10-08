package components

import components.layout.NoStateLayout
import components.layout.NoUserLayout
import components.layout.PresenterLayout
import components.layout.RootLayout
import csstype.*
import kotlinx.browser.window
import kotlinx.js.timers.setTimeout
import kotlinx.serialization.decodeFromString
import mui.material.*
import org.w3c.dom.CloseEvent
import org.w3c.dom.WebSocket
import react.*
import react.router.Route
import react.router.Router
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
        NoStateLayout {
            this.stale = stale
        }
        return@FC
    }

    AppStateContext.Provider {
        value = ClientAppState(appState ?: error("No app state!"), stale)

        val layout = if (appState?.session?.user == null) {
            NoUserLayout
        } else {
            RootLayout
        }

        BrowserRouter {
            Routes {
                Route {
                    path = "/*"
                    index = true
                    element = layout.create()
                }
                Route {
                    path = "presenter"
                    element = PresenterLayout.create()
                }
            }
        }
    }
}