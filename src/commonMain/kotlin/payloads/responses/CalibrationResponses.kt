package payloads.responses

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import tools.confido.calibration.CalibrationBin
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.serialization.RangeSerializer

@Serializable
sealed class CalibrationQuestion(
) {
    abstract val question: Ref<Question>
    abstract val scoredPrediction: Prediction
    abstract val scoreTime: Instant
    abstract val confidence: Double
    abstract val bin: CalibrationBin
    abstract val isCorrect: Boolean
}

@Serializable
data class BinaryCalibrationQuestion(
    override val question: Ref<Question>,
    override val scoredPrediction: Prediction,
    override val scoreTime: Instant,
    override val confidence: Double,
    override val bin: CalibrationBin,
    val expectedOutcome: Boolean,
    val actualOutcome: Boolean,
) : CalibrationQuestion() {
    override val isCorrect get() = (expectedOutcome == actualOutcome)
}


@Serializable
data class NumericCalibrationQuestion(
    override val question: Ref<Question>,
    override val scoredPrediction: Prediction,
    override val scoreTime: Instant,
    override val confidence: Double,
    override val bin: CalibrationBin,
    @Serializable(with = RangeSerializer::class)
    val confidenceInterval: ClosedFloatingPointRange<Double>,
    val resolution: Double,
): CalibrationQuestion() {
    override val isCorrect get() = resolution in confidenceInterval
}