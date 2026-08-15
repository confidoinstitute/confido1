package tools.confido.state

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import rooms.Room
import tools.confido.serialization.confidoJSON
import kotlinx.serialization.json.decodeFromDynamic
import tools.confido.extensions.ClientExtension
import web.window.window

/**
 * The client's global-state facade, now reduced to the data maps: questions payload
 * + rooms directory (what shared-code `deref()` resolves against via [globalState]).
 * Self-derived state (own user, permissions, presenter info, own predictions) is
 * read through the SelfContext accessors (`useMyUser`/`usePermission`/…) — [self]
 * is kept here only as the source the provider feeds SelfContext from and for the
 * few non-React readers of the [clientState] global.
 * Note [rooms] holds *directory-level* rooms (own membership, no invite links) —
 * the full room travels on the per-room detail topic and is provided via
 * RoomContext by the room page.
 */
class ClientState(
    val questionsState: QuestionsState,
    val self: SelfState,
    val roomDir: Map<String, Room>,
) : GlobalState() {
    override val rooms get() = roomDir
    override val questions get() = questionsState.questions
    override val predictionCount get() = questionsState.predictionCount
    override val predictorCount get() = questionsState.predictorCount
    override val commentCount get() = questionsState.commentCount

    override val extensions: List<ClientExtension> get() =
        ClientExtension.enabled
}

var clientState: ClientState = ClientState(QuestionsState(), SelfState(), emptyMap())
actual val globalState: GlobalState get() = clientState


@OptIn(ExperimentalSerializationApi::class)
actual val appConfig: AppConfig = Json.decodeFromDynamic(window.asDynamic().appConfig)
