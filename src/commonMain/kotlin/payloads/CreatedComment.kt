package payloads

@kotlinx.serialization.Serializable
data class CreatedComment(
    val timestamp: Int,
    val content: String,
    val attachPrediction: Boolean,
)