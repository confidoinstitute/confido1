package components

import web.window.window
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.encodeToDynamic
import payloads.responses.WSData
import payloads.responses.WSResponse
import react.*
import tools.confido.serialization.confidoJSON
import tools.confido.state.ClientState
import tools.confido.state.SentState
import tools.confido.state.clientState
import websocket.MultiplexWS

external interface NoAppStateProps : Props {
    var stale : Boolean
}

external interface AppStateWebsocketProviderProps : PropsWithChildren {
    var loadingComponent : ComponentType<NoAppStateProps>?
}

/**
 * Owns the multiplexed websocket connection and provides [AppStateContext] from
 * the "/state" topic. All other topics share the same connection via
 * `useSubscription` (PERSISTENCE_SYNC_REDESIGN.md §4.1).
 */
val AppStateWebsocketProvider = FC<AppStateWebsocketProviderProps> { props ->
    val loginState = use(LoginContext)!!
    var appState by useState<SentState?>(null)
    var stale by useState(false)

    useEffect(loginState) {
        MultiplexWS.onUnauthorized = { loginState.logout() }
    }

    useEffectOnceWithCleanup {
        val unsubscribe = MultiplexWS.subscribe("/state") { element, isStale ->
            if (element != null) {
                // decodeFromString, not decodeFromJsonElement — see useSubscription for why.
                val response: WSResponse<SentState> = confidoJSON.decodeFromString(element.toString())
                (response as? WSData)?.data?.let { decodedState ->
                    clientState = ClientState(decodedState)
                    appState = decodedState

                    try {
                        @OptIn(ExperimentalSerializationApi::class)
                        window.asDynamic().curState =
                            confidoJSON.encodeToDynamic(decodedState) // for easy inspection in devtools
                    } catch (e: Exception) {
                    }
                }
            }
            stale = isStale
        }
        MultiplexWS.start()
        onCleanup { unsubscribe() }
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
