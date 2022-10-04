package tools.confido.state

import kotlinx.serialization.Serializable
import tools.confido.question.Comment
import tools.confido.question.Prediction
import tools.confido.question.Room

@Serializable
data class AppState(
    val rooms: List<Room>,
    val userPredictions: Map<String, Prediction>,
    val comments: Map<String, List<Comment>>,
    val groupDistributions: Map<String, List<Double>>,
    val session: UserSession,
    val isAdmin: Boolean = false,
) {
    fun getRoom(id: String): Room? {
        return rooms.find { it.id == id }
    }
}
