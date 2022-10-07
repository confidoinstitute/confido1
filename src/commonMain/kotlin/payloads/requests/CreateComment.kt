package payloads.requests

@kotlinx.serialization.Serializable
data class CreateComment(
    val timestamp: Int,
    val content: String,
    val attachPrediction: Boolean,
)