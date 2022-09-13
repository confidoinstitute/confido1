package tools.confido.question

@kotlinx.serialization.Serializable
data class Question(
    val id: String,
    val name: String,
    var visible: Boolean,
    var answerSpace: AnswerSpace,
)

@kotlinx.serialization.Serializable
sealed interface AnswerSpace {
    val bins: Int
    fun verifyPrediction(prediction: Prediction): Boolean
}

@kotlinx.serialization.Serializable
class BinaryAnswerSpace() : AnswerSpace {
    override val bins: Int = 2
    override fun verifyPrediction(prediction: Prediction): Boolean {
        val pred = prediction as? BinaryPrediction ?: return false
        return (pred.estimate in 0.0..1.0)
    }
}

@kotlinx.serialization.Serializable
class NumericAnswerSpace(
    override val bins: Int,
    val min: Double,
    val max: Double,
) : AnswerSpace {
    override fun verifyPrediction(prediction: Prediction): Boolean {
        val pred = prediction as? NumericPrediction ?: return false
        return (pred.mean in min..max && pred.stdDev in 0.0..(max-min)/2)
    }
}

@kotlinx.serialization.Serializable
sealed interface Prediction {
}

@kotlinx.serialization.Serializable
data class NumericPrediction (
    val mean: Double,
    val stdDev: Double,
) : Prediction

@kotlinx.serialization.Serializable
data class BinaryPrediction (
    val estimate: Double
) : Prediction

@kotlinx.serialization.Serializable
sealed interface Answer {
    fun represent(): String
    fun toBins(bins: Int): List<Double>
}
