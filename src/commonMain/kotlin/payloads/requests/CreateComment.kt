package payloads.requests

import kotlinx.serialization.EncodeDefault
import tools.confido.utils.unixNow

/**
 * Create a new comment of an undetermined type (type is decided by POST URL). The parameter [attachPrediction] is ignored for room comments.
 */
@kotlinx.serialization.Serializable
data class CreateComment(
    @EncodeDefault
    val timestamp: Int = unixNow(),
    val content: String,
    val attachPrediction: Boolean = false,
)