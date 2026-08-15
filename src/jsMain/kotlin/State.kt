package tools.confido.state

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import rooms.Room
import rooms.RoomPermission
import tools.confido.serialization.confidoJSON
import kotlinx.serialization.json.decodeFromDynamic
import tools.confido.extensions.ClientExtension
import users.UserType
import web.window.window

/**
 * The client's global-state facade: assembles the shrinking "/state" monolith
 * ([SentState]) and the peeled per-topic slices ([SelfState], the rooms directory)
 * into one coherent view. Components read it via `AppStateContext`; shared-code
 * `deref()` resolves against it via [globalState]. As Phase 3 decomposition
 * proceeds, fields migrate from [sentState] to topic-backed slices without call
 * sites changing. Note [rooms] holds *directory-level* rooms (own membership,
 * no invite links) — the full room travels on the per-room detail topic and is
 * provided via RoomContext by the room page.
 */
class ClientState(
    val sentState: SentState,
    val self: SelfState,
    val roomDir: Map<String, Room>,
) : GlobalState() {
    override val rooms get() = roomDir
    override val questions get() = sentState.questions
    override val users get() = sentState.users
    override val predictionCount get() = sentState.predictionCount
    override val predictorCount get() = sentState.predictorCount
    override val commentCount get() = sentState.commentCount

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

var clientState: ClientState = ClientState(SentState(), SelfState(), emptyMap())
actual val globalState: GlobalState get() = clientState

fun Room.havePermission(permission: RoomPermission): Boolean {
    val myself = clientState.session.user
    return hasPermission(myself, permission)
}

@OptIn(ExperimentalSerializationApi::class)
actual val appConfig: AppConfig = Json.decodeFromDynamic(window.asDynamic().appConfig)
