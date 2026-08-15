package tools.confido.state

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import rooms.Room
import rooms.RoomPermission
import tools.confido.serialization.confidoJSON
import kotlinx.serialization.json.decodeFromDynamic
import tools.confido.extensions.ClientExtension
import users.User
import users.UserType
import web.window.window

/**
 * The client's global-state facade: assembles the topic slices — the questions
 * payload, the rooms directory, the users map and [SelfState] — into one coherent
 * view (the former SentState monolith is fully decomposed). Components read it via
 * `AppStateContext`; shared-code `deref()` resolves against it via [globalState].
 * Note [rooms] holds *directory-level* rooms (own membership, no invite links) —
 * the full room travels on the per-room detail topic and is provided via
 * RoomContext by the room page.
 */
class ClientState(
    val questionsState: QuestionsState,
    val self: SelfState,
    val roomDir: Map<String, Room>,
    override val users: Map<String, User>,
) : GlobalState() {
    override val rooms get() = roomDir
    override val questions get() = questionsState.questions
    override val predictionCount get() = questionsState.predictionCount
    override val predictorCount get() = questionsState.predictorCount
    override val commentCount get() = questionsState.commentCount

    val session by self::session
    val myPredictions by self::myPredictions
    val myPasswordIsSet by self::myPasswordIsSet
    val presenterWindowActive by self::presenterWindowActive
    override val extensions: List<ClientExtension> get() =
        ClientExtension.enabled

    fun isAdmin() = session.user?.type == UserType.ADMIN
    fun hasPermission(room: Room, permission: RoomPermission) =
        room.hasPermission(session.user, permission)
    fun hasAnyPermission(room: Room, vararg permissions: RoomPermission) =
        permissions.any { room.hasPermission(session.user, it) }
    fun hasAllPermissions(room: Room, vararg permissions: RoomPermission) =
        permissions.all { room.hasPermission(session.user, it) }

    val isAnonymous get() = session.user?.isAnonymous() ?: true
    val isFullUser get() = session.user?.type?.isProper() ?: false
}

var clientState: ClientState = ClientState(QuestionsState(), SelfState(), emptyMap(), emptyMap())
actual val globalState: GlobalState get() = clientState

fun Room.havePermission(permission: RoomPermission): Boolean {
    val myself = clientState.session.user
    return hasPermission(myself, permission)
}

@OptIn(ExperimentalSerializationApi::class)
actual val appConfig: AppConfig = Json.decodeFromDynamic(window.asDynamic().appConfig)
