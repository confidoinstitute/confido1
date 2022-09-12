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
}

@kotlinx.serialization.Serializable
data class NumericParams(
    val mean: Double,
    val stdDev: Double,
)

@kotlinx.serialization.Serializable
class BinaryAnswerSpace() : AnswerSpace {
    override val bins: Int = 2
}

@kotlinx.serialization.Serializable
class NumericAnswerSpace(
    override val bins: Int,
    val min: Double,
    val max: Double,
) : AnswerSpace