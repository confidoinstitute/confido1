package tools.confido.question

import kotlinx.serialization.Serializable
import tools.confido.distributions.BinaryDistribution
import tools.confido.question.Prediction
import tools.confido.users.UserPresentation

@Serializable
data class BinaryHistogram(
    val bins: List<HistogramBin>,
    val median: Double?,
    val mean: Double?,
)

@Serializable
data class HistogramBin(
    val min: Double,
    val max: Double,
    val count: Int = 0,
    /**
     * Up to [BinaryHistogramBinner.MAX_USERS_PER_BIN] users whose predictions fell into this bin.
     * Null when the per-bin user list feature is disabled / not authorized for the requesting user;
     * empty when the feature is enabled but the bin contains no predictions.
     */
    val users: List<UserPresentation>? = null,
    /** Number of additional users in this bin not included in [users] (cap overflow). */
    val moreUsers: Int = 0,
) {
    val width = (max - min)
}

typealias HistogramBinningRule = List<HistogramBin>

val DefaultHistogramBinning = listOf(HistogramBin(0.0, 0.05)) +
                                (10..90 step 10).map { HistogramBin((it-5)/100.0, (it+5)/100.0) } +
                                listOf(HistogramBin(0.95, 1.0))

/**
 * Coarse binning for [Question.sensitive] questions: the default 5%-resolution bins
 * would disclose the (anonymous) multiset of predictions nearly exactly, enabling
 * value-fingerprinting ("the one who always says 97%"). Five fixed 20% bins cap that.
 * Fixed rather than adaptive-to-predictor-count on purpose — re-binning as the count
 * grows would let an observer intersect the two partitions across the transition and
 * localize values finer than either binning alone.
 */
val SensitiveHistogramBinning = (0 until 5).map { HistogramBin(it / 5.0, (it + 1) / 5.0) }

class BinaryHistogramBinner(private val bins: HistogramBinningRule = DefaultHistogramBinning) {
    init {
        require(bins.size > 0)
    }

    companion object {
        /** Maximum number of users listed per bin in the wire format. Excess is reflected in [HistogramBin.moreUsers]. */
        const val MAX_USERS_PER_BIN = 10
    }

    private fun valueToBin(value: BinaryDistribution): Int? {
        if (value.yesProb < 0 || value.yesProb > 1) return null
        // In case of yesProb = 1 we would get a value out of the range, so we clamp it
        return bins.indexOfFirst { value.yesProb >= it.min && value.yesProb <= it.max }
    }

    private fun binPredictions(predictions: Iterable<BinaryDistribution>): List<HistogramBin> {
        val counts = MutableList(bins.size) { 0 }
        for (dist in predictions) {
            valueToBin(dist)?.let { counts[it]++ }
        }
        return counts.mapIndexed { index, count ->
            bins[index].copy(count = count)
        }
    }

    private fun binPredictionsWithUsers(
        predictions: Iterable<Pair<BinaryDistribution, UserPresentation>>,
    ): List<HistogramBin> {
        val perBinUsers = MutableList(bins.size) { mutableListOf<UserPresentation>() }
        val perBinTotal = MutableList(bins.size) { 0 }
        for ((dist, presentation) in predictions) {
            val idx = valueToBin(dist) ?: continue
            perBinTotal[idx]++
            perBinUsers[idx].add(presentation)
        }
        return bins.mapIndexed { index, template ->
            val all = perBinUsers[index]
            val shown = all.take(MAX_USERS_PER_BIN)
            val more = (all.size - shown.size).coerceAtLeast(0)
            template.copy(
                count = perBinTotal[index],
                users = shown,
                moreUsers = more,
            )
        }
    }

    private fun computeMeanAndMedian(yesProbs: List<Double>): Pair<Double?, Double?> {
        val mean = yesProbs.average().let { if (it.isNaN()) null else it }
        val sorted = yesProbs.sorted()
        val median = if (sorted.isEmpty()) {
            null
        } else if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2] + sorted[sorted.size / 2 - 1]) / 2
        } else {
            sorted[sorted.size / 2]
        }
        return mean to median
    }

    fun createHistogram(predictions: Iterable<BinaryDistribution>): BinaryHistogram {
        val bins = binPredictions(predictions)
        val (mean, median) = computeMeanAndMedian(predictions.map { it.yesProb })
        return BinaryHistogram(bins, median, mean)
    }

    /**
     * Like [createHistogram] but also records up to [MAX_USERS_PER_BIN] [UserPresentation]s per bin.
     * The caller is responsible for applying any visibility/pseudonymization policy when
     * constructing each [UserPresentation].
     */
    fun createHistogramWithUsers(
        predictions: Iterable<Pair<BinaryDistribution, UserPresentation>>,
    ): BinaryHistogram {
        val materialized = predictions.toList()
        val bins = binPredictionsWithUsers(materialized)
        val (mean, median) = computeMeanAndMedian(materialized.map { it.first.yesProb })
        return BinaryHistogram(bins, median, mean)
    }
}
