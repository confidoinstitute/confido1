package extensions.calibration_training

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import rooms.Room
import tools.confido.calibration.CalibrationVector
import tools.confido.extensions.Extension
import tools.confido.refs.Ref
import tools.confido.state.FlowAction
import tools.confido.state.PresenterView
import users.User
import kotlinx.serialization.serializer
import tools.confido.utils.TopicSpec0
import tools.confido.utils.path

/**
 * A training question loaded from the CSV/TSV file.
 */
@Serializable
data class TrainingQuestion(
    val id: String,
    val text: String,
    val answer: Boolean,
    val comment: String? = null,
    val category: String? = null,
    val skip: Boolean = false,
)

/**
 * A user's response to a training question.
 * Stored in MongoDB.
 */
@Serializable
data class TrainingResponse(
    @SerialName("_id")
    val id: String = "",
    val user: Ref<User>,
    val questionId: String,
    val yesProb: Double,        // User's prediction (0.0 to 1.0)
    val timestamp: Long,
)

/**
 * Stats for a single calibration bucket.
 */
@Serializable
data class BucketStat(
    val correct: Int,
    val total: Int,
) {
    val accuracy: Double get() = if (total > 0) correct.toDouble() / total else 0.0
}

/**
 * Overall training statistics for a user.
 */
@Serializable
data class TrainingStats(
    val totalAnswered: Int,
    val bucketStats: Map<String, BucketStat>,  // bucket name -> stats
)

/**
 * Response from GET /api/training/next
 */
@Serializable
data class NextQuestionResponse(
    val question: TrainingQuestion?,  // null if no more unanswered questions
    val progress: TrainingProgress,
)

/**
 * Progress information.
 */
@Serializable
data class TrainingProgress(
    val answered: Int,
    val total: Int,
)

/**
 * Request for POST /api/training/submit
 */
@Serializable
data class SubmitAnswerRequest(
    val questionId: String,
    val yesProb: Double,
)

/**
 * Feedback for the user's confidence bucket.
 */
@Serializable
data class BucketFeedback(
    val bucketName: String,           // e.g., "75% - 85%"
    val correctInBucket: Int,
    val totalInBucket: Int,
    val targetAccuracy: Double,       // e.g., 0.80 for 80% bucket midpoint
)

/**
 * Response from POST /api/training/submit
 */
@Serializable
data class AnswerFeedbackResponse(
    val question: TrainingQuestion,
    val userPrediction: Double,
    val wasCorrectSide: Boolean,      // Did they predict the likely outcome correctly?
    val bucketFeedback: BucketFeedback,
)

/**
 * Leaderboard entry for calibration training.
 */
@Serializable
data class CalibrationLeaderboardEntry(
    val nickname: String,
    val score: Double,          // 0–100 calibration score
    val numAnswered: Int,
    val calibration: CalibrationVector,  // per-user calibration vector for drill-down graph
)

/**
 * Presenter view: calibration training leaderboard on the projector.
 */
@Serializable
@SerialName("calibration_training_leaderboard")
class CalibrationLeaderboardPV : PresenterView() {
    override fun describe() = "Calibration training leaderboard"
}

/**
 * Flow action: show calibration training leaderboard on presenter
 * and navigate followers to their personal stats page.
 */
@Serializable
@SerialName("showCalibrationLeaderboard")
class ShowCalibrationLeaderboardAction : FlowAction() {
    override fun describe() = "Show calibration training leaderboard"
    override val presenterView get() = CalibrationLeaderboardPV()
    override fun getNavigateUrl(room: Room) = "/training/stats"
}

/**
 * Base extension class shared between client and server.
 */
open class CalibrationTrainingExtension : Extension {
    override val extensionId = "calibration_training"

    override fun registerPresenterViews(builder: PolymorphicModuleBuilder<PresenterView>) {
        builder.subclass(CalibrationLeaderboardPV::class)
    }

    override fun registerFlowActions(builder: PolymorphicModuleBuilder<FlowAction>) {
        builder.subclass(ShowCalibrationLeaderboardAction::class)
    }
}

// Topic owned by this extension (see tools.confido.state.Topics for the scheme).
val calibrationLeaderboardTopic = TopicSpec0(path("ext/calibration_training/leaderboard"), serializer<List<CalibrationLeaderboardEntry>>())
