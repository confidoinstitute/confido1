package tools.confido.payloads.responses

import kotlinx.serialization.Serializable
import tools.confido.question.Prediction
import tools.confido.users.UserPresentation
import tools.confido.utils.List2

/**
 * Request for the per-user two-timepoint comparison: each predictor's prediction
 * as of two unix timestamps (null = the latest prediction). Backs the individual
 * tabs of the compare view (changes/scatter/table).
 */
@Serializable
data class UserPredCompareRequest(
    val ts1: Int? = null,
    val ts2: Int? = null,
)

/** One predictor's predictions at the two compared timepoints. */
@Serializable
data class UserPredCompareRow(
    val preds: List2<Prediction?>,
    /** How to present this predictor to the requesting user. */
    val user: UserPresentation? = null,
)
