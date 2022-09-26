package tools.confido.question

import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.distributions.TruncatedNormalDistribution
import tools.confido.utils.binRanges

@Serializable
data class Question(
    @SerialName("_id")
    val id: String,
    val name: String,
    val answerSpace: AnswerSpace,
    var visible: Boolean = true,
    var enabled: Boolean = true,
    var predictionsVisible: Boolean = false,
    var resolved: Boolean = false,
)

@Serializable
sealed class AnswerSpace {
    abstract val bins: Int
    abstract fun verifyParams(): Boolean
    abstract fun verifyPrediction(prediction: Prediction): Boolean
    abstract fun predictionToDistribution(prediction: Prediction): List<Double>
}

@Serializable
class BinaryAnswerSpace() : AnswerSpace() {
    override val bins: Int = 2
    override fun verifyParams() = true

    override fun verifyPrediction(prediction: Prediction): Boolean {
        val pred = prediction as? BinaryPrediction ?: return false
        return (pred.estimate in 0.0..1.0)
    }

    override fun predictionToDistribution(prediction: Prediction): List<Double> {
        val estimate = (prediction as BinaryPrediction).estimate
        return listOf(1 - estimate, estimate)
    }
}

@Serializable
class NumericAnswerSpace(
    override val bins: Int,
    val min: Double,
    val max: Double,
    val representsDays: Boolean = false
) : AnswerSpace() {
    override fun verifyParams() = (!min.isNaN() && !max.isNaN() && min < max)

    override fun verifyPrediction(prediction: Prediction): Boolean {
        val pred = prediction as? NumericPrediction ?: return false
        return (pred.mean in min..max && pred.stdDev in 0.0..(max-min)/2)
    }

    override fun predictionToDistribution(prediction: Prediction): List<Double> {
        val pred = prediction as NumericPrediction
        val distribution = TruncatedNormalDistribution(pred.mean, pred.stdDev, min, max)
        return binRanges(min, max, bins).map {(a, b) -> distribution.probabilityBetween(a, b)}
    }

    companion object {
        fun fromDates(minDate: LocalDate, maxDate: LocalDate): NumericAnswerSpace {
            val min = minDate.toEpochDays() * 86400.0
            val max = maxDate.toEpochDays() * 86400.0
            val bins = (maxDate - minDate).days
            return NumericAnswerSpace(bins, min, max, true)
        }
    }
}

@Serializable
sealed class Prediction {
}

@Serializable
data class NumericPrediction (
    val mean: Double,
    val stdDev: Double,
) : Prediction()

@Serializable
data class BinaryPrediction (
    val estimate: Double
) : Prediction()

@Serializable
sealed interface Answer {
    fun represent(): String
    fun toBins(bins: Int): List<Double>
}
