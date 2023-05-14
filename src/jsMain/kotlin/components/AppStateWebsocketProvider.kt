package components

import browser.window
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.encodeToDynamic
import react.*
import tools.confido.serialization.confidoJSON
import tools.confido.state.ClientState
import tools.confido.state.SentState
import tools.confido.state.appConfig
import tools.confido.state.clientState
import utils.webSocketUrl
import web.location.location
import web.timers.clearInterval
import web.timers.setInterval
import websockets.CloseEvent
import websockets.WebSocket

enum class WebsocketState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
}

external interface NoAppStateProps : Props {
    var stale : Boolean
}

external interface AppStateWebsocketProviderProps : PropsWithChildren {
    var loadingComponent : ComponentType<NoAppStateProps>?
}

val AppStateWebsocketProvider = FC<AppStateWebsocketProviderProps> { props ->
    val loginState = useContext(LoginContext)
    var appState by useState<SentState?>(null)
    var stale by useState(false)
    val (_, setWebsocketState) = useState(WebsocketState.DISCONNECTED)
    val webSocket = useRef<WebSocket>(null)

    fun startWebSocket() {
        setWebsocketState.invoke { current ->
            if (current != WebsocketState.DISCONNECTED) return@invoke current

            // These versions are used to check compatibility with the server.
            val bundleVer = window.asDynamic().bundleVer as String
            val appConfigVer = window.asDynamic().appConfigVer as String

            val ws = WebSocket(webSocketUrl("/state?bundleVer=${bundleVer}&appConfigVer=${appConfigVer}"))
            ws.apply {
                onmessage = {
                    val decodedState = confidoJSON.decodeFromString<SentState>(it.data.toString())
                    clientState = ClientState(decodedState)
                    appState = decodedState
                    stale = false
                    setWebsocketState(WebsocketState.CONNECTED)

                    try {
                        @OptIn(ExperimentalSerializationApi::class)
                        window.asDynamic().curState =
                            confidoJSON.encodeToDynamic(decodedState) // for easy inspection in devtools
                    } catch (e: Exception) {
                    }
                    @Suppress("RedundantUnitExpression")
                    Unit // This is not redundant, because assignment fails some weird type checks
                }
                onclose = {
                    console.log("Closed websocket")
                    setWebsocketState(WebsocketState.DISCONNECTED)
                    stale = true
                    webSocket.current = null
                    (it as? CloseEvent)?.let { event ->
                        if (appConfig.devMode) console.log("AppState Websocket closed: code ${event.code}")
                        if (event.code == 4001) {
                            if (appConfig.devMode) console.log("AppState Websocket closed: incompatible frontend version")
                            // Incompatible frontend version
                            location.reload()
                        }
                        if (event.code == 3000) {
                            // Unauthorized
                            if (appConfig.devMode) console.log("AppState Websocket closed: unauthorized")
                            loginState.logout()
                        }
                    }
                }
            }
            webSocket.current = ws
            return@invoke WebsocketState.CONNECTING
        }
    }

    useEffectOnce {
        val retryInterval = setInterval(::startWebSocket, 5000)
        startWebSocket()

        cleanup {
            clearInterval(retryInterval)
            webSocket.current?.apply {
                onclose = null
                close()
            }
        }
    }


    if (appState != null) {
        AppStateContext.Provider {
            value = ClientAppState(appState ?: error("No app state!"), stale)
            +props.children
        }
    } else {
        props.loadingComponent?.invoke {
            this.stale = stale
        }
    }
}