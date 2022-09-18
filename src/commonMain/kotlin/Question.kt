package tools.confido.question

import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: String,
    val name: String,
    var visible: Boolean,
    var answerSpace: AnswerSpace,
)

@Serializable
sealed class AnswerSpace {
    abstract val bins: Int
    abstract fun verifyPrediction(prediction: Prediction): Boolean
}

@Serializable
class BinaryAnswerSpace() : AnswerSpace() {
    override val bins: Int = 2
    override fun verifyPrediction(prediction: Prediction): Boolean {
        val pred = prediction as? BinaryPrediction ?: return false
        return (pred.estimate in 0.0..1.0)
    }
}

@Serializable
class NumericAnswerSpace(
    override val bins: Int,
    val min: Double,
    val max: Double,
    val representsDays: Boolean = false
) : AnswerSpace() {
    override fun verifyPrediction(prediction: Prediction): Boolean {
        val pred = prediction as? NumericPrediction ?: return false
        return (pred.mean in min..max && pred.stdDev in 0.0..(max-min)/2)
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
