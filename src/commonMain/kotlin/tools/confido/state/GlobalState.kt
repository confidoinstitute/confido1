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

// The "/state/questions" topic payload (§4.2): all questions the viewer can see, with
// the per-question count maps riding along — they are per-question facts that only
// ever existed as separate maps because the retired SentState monolith was flat.
// (The former "/state" monolith is fully decomposed: rooms → "/state/rooms" (+ the
// per-room detail topic), per-user fields → "/state/self", users → "/state/users",
// questions+counts → here. The client's ClientState facade assembles BaseState from
// these slices.)
@Serializable
data class QuestionsState(
    val questions: Map<String, Question> = emptyMap(),
    val predictionCount: Map<Ref<Question>, Int> = emptyMap(),
    val predictorCount: Map<Ref<Question>, Int> = emptyMap(),
    val commentCount: Map<Ref<Question>, Int> = emptyMap(),
)

// The "question/<id>/counts" topic payload: one question's per-question counts.
// A sibling of "question/<id>" rather than part of it, so count churn (every
// prediction and comment) never frames the entity topic — bare-ref surfaces
// (presenter tiles) subscribe this one for the live "N people answered" line.
@Serializable
data class QuestionCounts(
    val predictions: Int = 0,
    val predictors: Int = 0,
    val comments: Int = 0,
)

// The per-user state slice sent over the "/state/self" topic
// (PERSISTENCE_SYNC_REDESIGN.md §4.2). Small and per-user by nature; keeping it out
// of the shared topics makes those purely permission-class-shaped and stops session
// churn (renewals, presenter windows, own predictions) from invalidating them.
@Serializable
data class SelfState(
    /** The viewer's own User (password scrubbed). The client resolves `Ref<User>`
     *  against this — no user other than oneself is ever client-derefable (the users
     *  map left ordinary clients; other identities arrive as UserPresentations
     *  embedded in payloads, §5.3). */
    val user: User? = null,
    // The two session fields the client actually reads — the raw UserSession is
    // deliberately NOT shipped (it is a credential-bearing server entity; shipping
    // it also required pinning expiryTime so session renewals deduped away).
    val presenterInfo: PresenterInfo? = null,
    val followingPresentation: Ref<Room>? = null,
    val myPredictions: Map<Ref<Question>, Prediction> = emptyMap(),
    val myPasswordIsSet: Boolean = false,
    val presenterWindowActive: Boolean = false,
)