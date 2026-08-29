package tools.confido.payloads.responses

import kotlinx.serialization.Serializable

/**
 * One point of a question's group prediction history, as shown on the timeline
 * graph. For binary questions [mid] is the yes-probability; for numeric questions
 * it is the median and [lo]/[hi] bound the 80% confidence interval (the same
 * interval convention the prediction input uses).
 */
@Serializable
data class GroupPredHistoryPoint(
    val ts: Int,
    val mid: Double,
    val lo: Double? = null,
    val hi: Double? = null,
)

@Serializable
data class GroupPredHistory(
    val points: List<GroupPredHistoryPoint>,
    /** Total number of updates in the log ([points] may be downsampled). */
    val updates: Int,
)
