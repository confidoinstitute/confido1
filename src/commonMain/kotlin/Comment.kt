package tools.confido.question

import kotlinx.serialization.Serializable
import users.User

@Serializable
data class Comment(
    val user: User,
    val timestamp: Int,
    val content: String,
    val prediction: Prediction?
) {
    fun key() = "${user.id}__${timestamp}"
}