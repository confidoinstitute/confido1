package tools.confido.state

import kotlinx.serialization.Serializable
import rooms.Room
import rooms.RoomPermission
import tools.confido.extensions.Extension
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

    abstract val extensions: List<Extension>
}

// Client or server will provide concrete implementation
expect val globalState: GlobalState

// The shared (permission-class-shaped) state snapshot sent over the "/state" topic.
// Transitional Phase 3 monolith: decomposes field by field into the §4.2 semantic
// topics; the per-user fields already moved to [SelfState] (the "self" topic).
@Serializable
data class SentState(
    override val rooms: Map<String, Room> = emptyMap(),
    override val questions: Map<String, Question> = emptyMap(),
    override val users: Map<String, User> = emptyMap(),
    override val predictionCount: Map<Ref<Question>, Int> = emptyMap(),
    override val predictorCount: Map<Ref<Question>, Int> = emptyMap(),
    override val commentCount: Map<Ref<Question>, Int> = emptyMap(),
) : BaseState

// The per-user state slice sent over the "/state/self" topic
// (PERSISTENCE_SYNC_REDESIGN.md §4.2). Small and per-user by nature; keeping it out
// of [SentState] makes the snapshot purely permission-class-shaped and stops session
// churn (renewals, presenter windows, own predictions) from invalidating it.
@Serializable
data class SelfState(
    val session: UserSession = UserSession(),
    val myPredictions: Map<Ref<Question>, Prediction> = emptyMap(),
    val myPasswordIsSet: Boolean = false,
    val presenterWindowActive: Boolean = false,
)