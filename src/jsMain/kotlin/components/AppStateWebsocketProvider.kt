package components

import hooks.decodeTopicPayload
import payloads.responses.WSData
import react.*
import tools.confido.state.SelfState
import tools.confido.state.Topics
import websocket.MultiplexWS

external interface NoAppStateProps : Props {
    var stale : Boolean
}

external interface AppStateWebsocketProviderProps : PropsWithChildren {
    var loadingComponent : ComponentType<NoAppStateProps>?
}

/**
 * Owns the multiplexed websocket's lifecycle and the root `self` subscription
 * feeding [SelfContext] (the one slice every page needs — identity, permissions
 * inputs, own predictions). Everything else is subscribed by the surfaces that
 * display it (`useSubscription` and the accessors built on it); there is no
 * client-global state facade. Children render once the self payload has arrived,
 * so SelfContext is always populated below this point.
 */
val AppStateWebsocketProvider = FC<AppStateWebsocketProviderProps> { props ->
    val loginState = use(LoginContext)!!
    var self by useState<SelfState?>(null)
    // The debounced connection flag, mirrored from MultiplexWS (which owns the
    // debounce so that non-React code shares the same notion of "disconnected").
    var disconnected by useState(MultiplexWS.disconnected)

    useEffect(loginState) {
        MultiplexWS.onUnauthorized = { loginState.logout() }
    }

    useEffectOnceWithCleanup {
        val unsub = MultiplexWS.onConnectionChange { disconnected = it }
        onCleanup { unsub() }
    }

    useEffectOnceWithCleanup {
        val unsubSelf = MultiplexWS.subscribe(Topics.self().name, decodeTopicPayload(Topics.self())) { response, _ ->
            (response as? WSData)?.data?.let { self = it }
        }
        MultiplexWS.start()
        onCleanup { unsubSelf() }
    }

    ConnectionContext.Provider {
        value = disconnected
        if (self != null) {
            SelfContext.Provider {
                value = self ?: error("No self state!")
                +props.children
            }
        } else {
            props.loadingComponent?.invoke {
                this.stale = disconnected
            }
        }
    }
}
