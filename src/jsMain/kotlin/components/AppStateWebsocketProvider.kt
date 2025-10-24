package components

import web.window.window
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
import web.events.EventHandler
import web.location.location
import web.messaging.MessageEvent
import web.timers.clearInterval
import web.timers.setInterval
import web.sockets.CloseEvent
import web.sockets.WebSocket

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
    val loginState = use(LoginContext)!!
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
                onmessage = EventHandler { event: MessageEvent<*> ->
                    val decodedState = confidoJSON.decodeFromString<SentState>(event.data.toString())
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
                }
                onclose = EventHandler { event: CloseEvent->
                    console.log("Closed websocket")
                    setWebsocketState(WebsocketState.DISCONNECTED)
                    stale = true
                    webSocket.current = null
                    if (appConfig.devMode) console.log("AppState Websocket closed: code ${event.code}")
                    if (event.code.toInt() == 4001) {
                        if (appConfig.devMode) console.log("AppState Websocket closed: incompatible frontend version")
                        // Incompatible frontend version
                        location.reload()
                    }
                    if (event.code.toInt() == 3000) {
                        // Unauthorized
                        if (appConfig.devMode) console.log("AppState Websocket closed: unauthorized")
                        loginState.logout()
                    }
                }
            }
            webSocket.current = ws
            return@invoke WebsocketState.CONNECTING
        }
    }

    useEffectOnceWithCleanup {
        val retryInterval = setInterval(::startWebSocket, 5000)
        startWebSocket()

         onCleanup {
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