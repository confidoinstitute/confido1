package tools.confido.state

import kotlinx.serialization.Serializable
import tools.confido.question.Comment
import tools.confido.question.Prediction
import rooms.Room
import rooms.RoomPermission
import tools.confido.refs.*
import tools.confido.question.Question
import users.User
import users.UserType

abstract class GlobalState {
    val rooms:  MutableMap<String, Room> = mutableMapOf()
    val questions:  MutableMap<String, Question> = mutableMapOf()
    val users:  MutableMap<String, User> = mutableMapOf()

    // this has to be suspend because it can potentially fetch target entity
    // on demand from database (on server) or network (on client)
    @RefInternalAPI
    open fun <T: Entity> maybeDeref(collectionId: String, ref: Ref<T>): T? =
        when (collectionId) {
            "Question" -> questions[ref.id] as T?
            "User" -> users[ref.id] as T?
            "Room" -> rooms[ref.id] as T?
            else -> null
        }
    @RefInternalAPI
    open suspend fun <T: Entity> derefLazy(collectionId: String, ref: Ref<T>): T? = maybeDeref(collectionId, ref)
}

// Client or server will provide concrete implementation
expect val globalState: GlobalState

// State representation sent using websocket
@Serializable
data class SentState(
    val rooms: Map<Ref<Room>, Room>,
    val myPredictions: Map<Ref<Question>, Prediction>,
    val comments: Map<String, List<Comment>>,
    val groupDistributions: Map<String, List<Double>>,
    val session: UserSession,
) {

    fun isAdmin(): Boolean {
        return session.user?.deref()?.type == UserType.ADMIN
    }

    fun hasPermission(room: Room, permission: RoomPermission): Boolean {
        return room.hasPermission(session.user?.deref(), permission)
    }
}
