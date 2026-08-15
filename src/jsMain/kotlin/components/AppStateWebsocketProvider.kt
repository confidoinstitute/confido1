package components

import web.window.window
import hooks.decodeTopicPayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToDynamic
import payloads.responses.WSData
import react.*
import tools.confido.serialization.confidoJSON
import tools.confido.state.ClientState
import tools.confido.state.SelfState
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
 * Owns the root subscriptions of the multiplexed websocket and provides
 * [AppStateContext] as a [ClientState] facade assembled from the "/state" monolith
 * and the peeled "/state/self" slice. Both subscriptions issue in the same tick, so
 * their initial payloads arrive in one consistent frame; the global [clientState]
 * (which backs shared-code `deref()`) is reassigned inside the frame's listener
 * pass, i.e. always before React re-renders anything.
 */
val AppStateWebsocketProvider = FC<AppStateWebsocketProviderProps> { props ->
    val loginState = use(LoginContext)!!
    var appState by useState<ClientState?>(null)
    var stale by useState(false)

    useEffect(loginState) {
        MultiplexWS.onUnauthorized = { loginState.logout() }
    }

    useEffectOnceWithCleanup {
        var curSent: SentState? = null
        var curSelf: SelfState? = null
        fun apply() {
            val sent = curSent ?: return
            val self = curSelf ?: return
            val cs = ClientState(sent, self)
            clientState = cs
            appState = cs

            try {
                @OptIn(ExperimentalSerializationApi::class)
                window.asDynamic().curState =
                    confidoJSON.encodeToDynamic(sent) // for easy inspection in devtools
                @OptIn(ExperimentalSerializationApi::class)
                window.asDynamic().curSelf =
                    confidoJSON.encodeToDynamic(self)
            } catch (e: Exception) {
            }
        }
        val unsubState = MultiplexWS.subscribe("/state", decodeTopicPayload<SentState>("/state")) { response, isStale ->
            (response as? WSData)?.data?.let { curSent = it; apply() }
            stale = isStale
        }
        val unsubSelf = MultiplexWS.subscribe("/state/self", decodeTopicPayload<SelfState>("/state/self")) { response, _ ->
            (response as? WSData)?.data?.let { curSelf = it; apply() }
        }
        MultiplexWS.start()
        onCleanup {
            unsubState()
            unsubSelf()
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
