package payloads.responses

/**
 * The update history point for a given question (identified by URL), sent as a list item. Contains the [mean] and [probability buckets][probs] of the group prediction as it was at the given [timestamp][ts].
 */
@kotlinx.serialization.Serializable
data class DistributionUpdate(
    val ts: Int,
    val mean: Double?,
    val probs: List<Double>,
)