package payloads.responses

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.question.Question
import tools.confido.refs.Ref

@Serializable
data class MyScoreItem(
    val questionRef: Ref<Question>,
    val questionName: String,
    val roomRef: Ref<Room>,
    val roomName: String,
    val score: Double,
    /** yesProb from the scored BinaryDistribution */
    val prediction: Double,
    /** true = Yes, false = No */
    val resolution: Boolean,
    /** effectiveSchedule.score - when the prediction was frozen for scoring */
    val scoreTime: Instant,
    /** When the question state changed to RESOLVED (null if unknown) */
    val resolveTime: Instant?,
)

@Serializable
data class MyScoresResponse(
    val items: List<MyScoreItem>,
    val totalScore: Double,
)
