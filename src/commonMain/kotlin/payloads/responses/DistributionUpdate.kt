package payloads.responses

@kotlinx.serialization.Serializable
data class DistributionUpdate(
    val ts: Int,
    val mean: Double?,
    val probs: List<Double>,
)