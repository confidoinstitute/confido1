package tools.confido.state

import kotlinx.serialization.Serializable
import tools.confido.question.Comment
import tools.confido.question.Prediction
import rooms.Room
import rooms.RoomPermission
import users.UserType


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
