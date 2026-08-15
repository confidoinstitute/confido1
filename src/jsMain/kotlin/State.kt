package tools.confido.state

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import rooms.Room
import rooms.RoomPermission
import tools.confido.serialization.confidoJSON
import kotlinx.serialization.json.decodeFromDynamic
import tools.confido.extensions.ClientExtension
import tools.confido.refs.DelicateRefAPI
import tools.confido.refs.Entity
import users.User
import users.UserType
import web.window.window
import kotlin.reflect.KClass

/**
 * The client's global-state facade: assembles the topic slices — the questions
 * payload, the rooms directory and [SelfState] — into one coherent view (the
 * former SentState monolith is fully decomposed; there is no global users map).
 * Components read it via `AppStateContext`; shared-code `deref()` resolves
 * against it via [globalState].
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

    // The only client-derefable user is oneself, resolved from the self slice
    // (UserSession.user is a shared-code deref). Other identities arrive as
    // UserPresentations embedded in payloads; the users directory topic is a
    // surface-local subscription (admin directory, add-member dialog), not
    // part of the global state.
    @DelicateRefAPI
    override fun derefNonBlocking(entityType: KClass<*>, id: String): Entity? =
        if (entityType == User::class) self.user?.takeIf { it.id == id }
        else super.derefNonBlocking(entityType, id)

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

var clientState: ClientState = ClientState(QuestionsState(), SelfState(), emptyMap())
actual val globalState: GlobalState get() = clientState

fun Room.havePermission(permission: RoomPermission): Boolean {
    val myself = clientState.session.user
    return hasPermission(myself, permission)
}

@OptIn(ExperimentalSerializationApi::class)
actual val appConfig: AppConfig = Json.decodeFromDynamic(window.asDynamic().appConfig)
