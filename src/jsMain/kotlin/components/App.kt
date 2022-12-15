package components

import browser.window
import components.layout.*
import csstype.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic
import mui.material.*
import mui.material.styles.PaletteColor
import mui.material.styles.createPalette
import mui.material.styles.createTheme
import mui.system.ThemeProvider
import react.*
import react.router.Route
import react.router.Router
import react.router.Routes
import react.router.dom.BrowserRouter
import tools.confido.serialization.confidoJSON
import tools.confido.state.ClientState
import tools.confido.state.SentState
import tools.confido.state.clientState
import utils.buildObject
import utils.webSocketUrl
import web.timers.setTimeout
import web.location.location
import websockets.CloseEvent
import websockets.WebSocket

val AppStateContext = createContext<ClientAppState>()


data class ClientAppState(val state: SentState, val stale: Boolean = false)

val globalTheme = createTheme(
    buildObject {
        this.palette = buildObject {
            this.primary = buildObject<PaletteColor> {
                main = Color("#675491")
                light = Color("#9681c2")
                dark = Color("#3a2b63")
                contrastText = Color("#ffffff")
            }
            this.secondary = buildObject<PaletteColor> {
                main = Color("#55a3b5")
                light = Color("#88d4e7")
                dark = Color("#1c7485")
                contrastText = Color("#ffffff")
            }
        }
    }
)

val App = FC<Props> {
    console.log(globalTheme)
    var appState by useState<SentState?>(null)
    var stale by useState(false)
    val webSocket = useRef<WebSocket>(null)

    fun startWebSocket() {
        val ws = WebSocket(webSocketUrl("/state?bundleVer=${window.asDynamic().bundleVer as String}"))
        ws.apply {
            onmessage = {
                val decodedState = confidoJSON.decodeFromString<SentState>(it.data.toString())
                clientState = ClientState(decodedState)
                appState = decodedState

                try {
                    @OptIn(ExperimentalSerializationApi::class)
                    window.asDynamic().curState = confidoJSON.encodeToDynamic(decodedState) // for easy inspection in devtools
                } catch (e: Exception) {}
                stale = false
                @Suppress("RedundantUnitExpression")
                Unit // This is not redundant, because assignment fails some weird type checks
            }
            onclose = {
                console.log("Closed websocket")
                stale = true
                webSocket.current = null
                (it as? CloseEvent)?.let { event ->
                    if (event.code == 3000 || event.code == 4001)
                        location.reload()
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

    CssBaseline {}
    AppStateContext.Provider {
        value = ClientAppState(appState ?: error("No app state!"), stale)

        val loggedIn = appState?.session?.user !=null
        val isDemo = appState?.appConfig?.demoMode ?: false
        val layout = if (loggedIn) {
            RootLayout
        } else {
            NoUserLayout
        }

        ThemeProvider {
            this.theme = globalTheme
            BrowserRouter {
                Routes {
                    if (isDemo)
                        Route {
                            path = "/"
                            index = true
                            element = DemoLayout.create {}
                        }
                    Route {
                        path = "/*"
                        index = !isDemo
                        element = layout.create {
                            key = "layout"
                        }
                    }
                    Route {
                        path = "presenter"
                        element = PresenterLayout.create()
                    }
                }
            }
        }
    }
}