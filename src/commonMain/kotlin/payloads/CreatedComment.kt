package payloads

@kotlinx.serialization.Serializable
data class CreatedComment(
    val timestamp: Double,
    val content: String,
    val attachPrediction: Boolean,
)