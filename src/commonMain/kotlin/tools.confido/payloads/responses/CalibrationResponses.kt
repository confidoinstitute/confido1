package tools.confido.payloads.responses

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.calibration.CalibrationBin
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.rooms.Room
import tools.confido.spaces.NumericSpace
import tools.confido.serialization.RangeSerializer

@Serializable
sealed class CalibrationQuestion(
) {
    abstract val question: Ref<Question>
    /** Display data (name, room link target) carried on the payload itself, so the
     * detail view needs no workspace-wide question subscription to render. */
    abstract val questionName: String
    abstract val room: Ref<Room>?
    abstract val scoredPrediction: Prediction
    abstract val scoreTime: Instant
    abstract val confidence: Double
    abstract val bin: CalibrationBin
    abstract val isCorrect: Boolean
}

@Serializable
@SerialName("binary")
data class BinaryCalibrationQuestion(
    override val question: Ref<Question>,
    override val questionName: String = "",
    override val room: Ref<Room>? = null,
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
@SerialName("numeric")
data class NumericCalibrationQuestion(
    override val question: Ref<Question>,
    override val questionName: String = "",
    override val room: Ref<Room>? = null,
    /** The question's numeric space, for interval/resolution formatting. */
    val space: NumericSpace? = null,
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