package tools.confido.question

import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val user: String,
    val timestamp: Int,
    val content: String,
    val prediction: Prediction?
) {
    fun key() = "${user}__${timestamp}"
}