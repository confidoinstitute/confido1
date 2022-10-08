package tools.confido.state

import kotlinx.serialization.Serializable
import tools.confido.question.Comment
import tools.confido.question.Prediction
import rooms.Room
import tools.confido.refs.*
import tools.confido.question.Question
import users.User

abstract class GlobalState {
    abstract val rooms:  Map<String, Room>
    abstract val questions:  Map<String, Question>
    abstract val users:  Map<String, User>

    // This function should dereference the entity if it is possible to do so
    // without suspending, return null otherwise.
    // This is an internal function, most code should use the
    // Ref<T>.{deref,maybeDeref,derefLazy} extensions methods
    // from tools.confido.refs.
    @RefInternalAPI
    open fun  derefNonBlocking(collectionId: String, id: String): Entity? =
        when (collectionId) {
            "Question" -> questions[id]
            "User" -> users[id]
            "Room" -> rooms[id]
            else -> null
        }
    // This function should dereference the entity, even if it involves blocking
    // (e.g. loading it from database or network). It may fail with all kinds
    // of exceptions.
    // This is an internal function, most code should use the
    // Ref<T>.{deref,maybeDeref,derefLazy} extensions methods
    // from tools.confido.refs.
    @RefInternalAPI
    open suspend fun  derefBlocking(collectionId: String, id: String): Entity? =
        derefNonBlocking(collectionId, id)
}

// Client or server will provide concrete implementation
expect val globalState: GlobalState

// State representation sent using websocket
@Serializable
data class SentState(
    val rooms: Map<String, Room> = emptyMap(),
    val questions: Map<String, Question> = emptyMap(),
    val users: Map<String, User> = emptyMap(),
    val myPredictions: Map<String, Prediction> = emptyMap(),
    val comments: Map<String, List<Comment>> = emptyMap(),
    val groupDistributions: Map<String, List<Double>> = emptyMap(),
    val session: UserSession,
) {

}
