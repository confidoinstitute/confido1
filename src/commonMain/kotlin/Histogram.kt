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
data class HistogramBin(val min: Double, val max: Double, val count: Int = 0) {
    val width = (max - min)
}

typealias HistogramBinningRule = List<HistogramBin>

val DefaultHistogramBinning = listOf(HistogramBin(0.0, 0.05)) +
                                (10..90 step 10).map { HistogramBin((it-5)/100.0, (it+5)/100.0) } +
                                listOf(HistogramBin(0.95, 1.0))

class BinaryHistogramBinner(private val bins: HistogramBinningRule = DefaultHistogramBinning) {
    init {
        require(bins.size > 0)
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
