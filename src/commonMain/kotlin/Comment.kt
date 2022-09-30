package tools.confido.question

import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val user: String,
    val timestamp: Double,
    val content: String,
    val prediction: Prediction?
) {
    fun key() = "${user}__${timestamp}"
}