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
import tools.confido.state.clientState
import users.User
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
 * (questions+counts, rooms directory, users, self — the former SentState monolith
 * is fully decomposed). All root subscriptions issue in the same tick, so their
 * initial payloads arrive in one consistent frame; the global [clientState]
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
        var curQuestions: QuestionsState? = null
        var curSelf: SelfState? = null
        var curRooms: Map<String, Room>? = null
        var curUsers: Map<String, User>? = null
        fun apply() {
            val cs = ClientState(
                curQuestions ?: return,
                curSelf ?: return,
                curRooms ?: return,
                curUsers ?: return,
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
                @OptIn(ExperimentalSerializationApi::class)
                window.asDynamic().curUsers = confidoJSON.encodeToDynamic(cs.users)
            } catch (e: Exception) {
            }
        }
        val unsubQuestions = MultiplexWS.subscribe("/state/questions", decodeTopicPayload<QuestionsState>("/state/questions")) { response, _ ->
            (response as? WSData)?.data?.let { curQuestions = it; apply() }
        }
        val unsubSelf = MultiplexWS.subscribe("/state/self", decodeTopicPayload<SelfState>("/state/self")) { response, _ ->
            (response as? WSData)?.data?.let { curSelf = it; apply() }
        }
        val unsubRooms = MultiplexWS.subscribe("/state/rooms", decodeTopicPayload<Map<String, Room>>("/state/rooms")) { response, isStale ->
            (response as? WSData)?.data?.let { curRooms = it; apply() }
            // Staleness is connection-level; tracking it on one root topic suffices.
            stale = isStale
        }
        val unsubUsers = MultiplexWS.subscribe("/state/users", decodeTopicPayload<Map<String, User>>("/state/users")) { response, _ ->
            (response as? WSData)?.data?.let { curUsers = it; apply() }
        }
        MultiplexWS.start()
        onCleanup {
            unsubQuestions()
            unsubSelf()
            unsubRooms()
            unsubUsers()
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
