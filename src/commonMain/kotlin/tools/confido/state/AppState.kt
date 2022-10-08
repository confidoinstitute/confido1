package tools.confido.state

import kotlinx.serialization.Serializable
import tools.confido.question.Comment
import tools.confido.question.Prediction
import rooms.Room
import rooms.RoomPermission
import tools.confido.eqid.Entity
import tools.confido.eqid.Ref
import tools.confido.eqid.RefInternalAPI
import tools.confido.question.Question
import users.UserType

open class GlobalState {
    val rooms:  MutableMap<String, Room> = mutableMapOf()
    val questions:  MutableMap<String, Question> = mutableMapOf()

    inline fun <reified T: Entity> deref(ref: Ref<T>): T? {
        val collectionId = T::class.simpleName!!
        @OptIn(RefInternalAPI::class)
        return deref(collectionId, ref)
    }
    @RefInternalAPI
    fun <T: Entity> deref(collectionId: String, ref: Ref<T>): T? =
        when (collectionId) {
            "Question" -> questions[ref.id] as T?
            "Room" -> rooms[ref.id] as T?
            else -> null
        }
}

expect val globalState: GlobalState

@Serializable
data class AppState(
    val rooms: List<Room>,
    val userPredictions: Map<String, Prediction>,
    val comments: Map<String, List<Comment>>,
    val groupDistributions: Map<String, List<Double>>,
    val session: UserSession,
) {
    fun getRoom(id: String): Room? {
        return rooms.find { it.id == id }
    }

    fun isAdmin(): Boolean {
        return session.user?.type == UserType.ADMIN
    }

    fun hasPermission(room: Room, permission: RoomPermission): Boolean {
        return room.hasPermission(session.user, permission)
    }
}
