package tools.confido.state

import kotlinx.serialization.Serializable
import rooms.Room
import rooms.RoomPermission
import tools.confido.question.*
import tools.confido.refs.*
import users.User
import users.UserType
import kotlin.reflect.KClass

interface BaseState {
    val rooms:  Map<String, Room>
    val questions:  Map<String, Question>
    val users:  Map<String, User>
    val predictorCount : Map<Ref<Question>, Int>
    val predictionCount : Map<Ref<Question>, Int>
    val commentCount : Map<Ref<Question>, Int>
}

abstract class GlobalState : BaseState {

    // This function should dereference the entity if it is possible to do so
    // without suspending, return null otherwise.
    // This is an internal function, most code should use the
    // Ref<T>.{deref,maybeDeref,derefLazy} extensions methods
    // from tools.confido.refs.
    @DelicateRefAPI
    open fun  derefNonBlocking(entityType: KClass<*>, id: String): Entity? =
        when (entityType) {
            Question::class -> questions[id]
            User::class -> users[id]
            Room::class -> rooms[id]
            else -> null
        }
    // This function should dereference the entity, even if it involves blocking
    // (e.g. loading it from database or network). It may fail with all kinds
    // of exceptions.
    // This is an internal function, most code should use the
    // Ref<T>.{deref,maybeDeref,derefLazy} extensions methods
    // from tools.confido.refs.
    @DelicateRefAPI
    open suspend fun  derefBlocking(entityType: KClass<*>, id: String): Entity? =
        derefNonBlocking(entityType, id)

    inline fun <reified T: ImmediateDerefEntity> get(id: String) = Ref<T>(id).deref()
    inline fun <reified T: ImmediateDerefEntity> getRef(id: String): Ref<T>? {
        val ref = Ref<T>(id)
        ref.deref() ?: return null
        return ref
    }
}

// Client or server will provide concrete implementation
expect val globalState: GlobalState

// State representation sent using websocket
@Serializable
data class SentState(
    override val rooms: Map<String, Room> = emptyMap(),
    override val questions: Map<String, Question> = emptyMap(),
    override val users: Map<String, User> = emptyMap(),
    val myPredictions: Map<Ref<Question>, Prediction> = emptyMap(),
    val session: UserSession = UserSession(),
    override val predictionCount: Map<Ref<Question>, Int> = emptyMap(),
    override val predictorCount: Map<Ref<Question>, Int> = emptyMap(),
    override val commentCount: Map<Ref<Question>, Int> = emptyMap(),
    val myPasswordIsSet: Boolean = false,
    val presenterWindowActive: Boolean = false,
) : BaseState {
    fun isAdmin(): Boolean {
        return session.user?.type == UserType.ADMIN
    }
    fun hasPermission(room: Room, permission: RoomPermission): Boolean {
        return room.hasPermission(session.user, permission)
    }
    fun hasAnyPermission(room: Room, vararg permissions: RoomPermission): Boolean {
        return permissions.any { room.hasPermission(session.user, it) }
    }
    fun hasAllPermissions(room: Room, vararg permissions: RoomPermission): Boolean {
        return permissions.all { room.hasPermission(session.user, it) }
    }

    val isAnonymous get() = session.user?.isAnonymous() ?: true
    val isFullUser get() = session.user?.type?.isProper() ?: false
}