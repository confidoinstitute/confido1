import kotlinx.serialization.Serializable
import tools.confido.distributions.BinaryDistribution
import tools.confido.question.Prediction

@Serializable
data class BinaryHistogram(
    val bins: List<HistogramBin>,
    val median: Double?,
    val mean: Double?,
)

@Serializable
data class HistogramBin(val min: Double, val max: Double, val count: Int)

class BinaryHistogramBinner(private val bins: Int) {
    init {
        require(bins > 0)
    }

    private val binSize = 1.0 / (bins + 1)

    private fun valueToBin(value: BinaryDistribution): Int? {
        if (value.yesProb < 0 || value.yesProb > 1) return null
        // In case of yesProb = 1 we would get a value out of the range, so we clamp it
        return minOf((value.yesProb / binSize).toInt(), bins)
    }

    private fun binPredictions(predictions: Iterable<BinaryDistribution>): List<HistogramBin> {
        val counts = MutableList(bins + 1) { 0 }
        for (dist in predictions) {
            valueToBin(dist)?.let { counts[it]++ }
        }
        return counts.mapIndexed { index, count ->
            val min = index * binSize
            val max = (index + 1) * binSize
            HistogramBin(min, max, count)
        }
    }

    fun createHistogram(predictions: Iterable<BinaryDistribution>): BinaryHistogram {
        val bins = binPredictions(predictions)

        val mean = predictions.map { it.yesProb }.average().let { if (it.isNaN()) null else it }
        val median = predictions.map { it.yesProb }.sorted().let {
            if (it.isEmpty()) {
                null
            } else if (it.size % 2 == 0) {
                (it[it.size / 2] + it[it.size / 2 - 1]) / 2
            } else {
                it[it.size / 2]
            }
        }

        return BinaryHistogram(bins, median, mean)
    }
}
