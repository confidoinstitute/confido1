package payloads.requests

/**
 * Create a new comment of an undetermined type (type is decided by POST URL). The parameter [attachPrediction] is ignored for room comments.
 */
@kotlinx.serialization.Serializable
data class CreateComment(
    val timestamp: Int,
    val content: String,
    val attachPrediction: Boolean,
)