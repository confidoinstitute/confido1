package components

import web.window.window
import hooks.decodeTopicPayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToDynamic
import payloads.responses.WSData
import react.*
import rooms.Room
import tools.confido.serialization.confidoJSON
import tools.confido.state.ClientState
import tools.confido.state.QuestionsState
import tools.confido.state.SelfState
import tools.confido.state.Topics
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
 * [AppStateContext] as a [ClientState] facade assembled from the topic slices
 * (questions+counts, rooms directory, self — the former SentState monolith is
 * fully decomposed; the users directory is a surface-local subscription, not a
 * root slice). All root subscriptions issue in the same tick, so their
 * initial payloads arrive in one consistent frame; the global [clientState]
 * (which backs shared-code `deref()`) is reassigned inside the frame's listener
 * pass, i.e. always before React re-renders anything.
 */
val AppStateWebsocketProvider = FC<AppStateWebsocketProviderProps> { props ->
    val loginState = use(LoginContext)!!
    var appState by useState<ClientState?>(null)
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
        var curQuestions: QuestionsState? = null
        var curSelf: SelfState? = null
        var curRooms: Map<String, Room>? = null
        fun apply() {
            val cs = ClientState(
                curQuestions ?: return,
                curSelf ?: return,
                curRooms ?: return,
            )
            clientState = cs
            appState = cs

            try {
                // For easy inspection in devtools.
                @OptIn(ExperimentalSerializationApi::class)
                window.asDynamic().curQuestions = confidoJSON.encodeToDynamic(cs.questionsState)
                @OptIn(ExperimentalSerializationApi::class)
                window.asDynamic().curSelf = confidoJSON.encodeToDynamic(cs.self)
                @OptIn(ExperimentalSerializationApi::class)
                window.asDynamic().curRooms = confidoJSON.encodeToDynamic(cs.roomDir)
            } catch (e: Exception) {
            }
        }
        val unsubQuestions = MultiplexWS.subscribe(Topics.questions().name, decodeTopicPayload(Topics.questions())) { response, _ ->
            (response as? WSData)?.data?.let { curQuestions = it; apply() }
        }
        val unsubSelf = MultiplexWS.subscribe(Topics.self().name, decodeTopicPayload(Topics.self())) { response, _ ->
            (response as? WSData)?.data?.let { curSelf = it; apply() }
        }
        val unsubRooms = MultiplexWS.subscribe(Topics.rooms().name, decodeTopicPayload(Topics.rooms())) { response, _ ->
            (response as? WSData)?.data?.let { curRooms = it; apply() }
        }
        MultiplexWS.start()
        onCleanup {
            unsubQuestions()
            unsubSelf()
            unsubRooms()
        }
    }

    ConnectionContext.Provider {
        value = disconnected
        if (appState != null) {
            SelfContext.Provider {
                value = (appState ?: error("No app state!")).self
                AppStateContext.Provider {
                    value = appState ?: error("No app state!")
                    +props.children
                }
            }
        } else {
            props.loadingComponent?.invoke {
                this.stale = disconnected
            }
        }
    }
}
