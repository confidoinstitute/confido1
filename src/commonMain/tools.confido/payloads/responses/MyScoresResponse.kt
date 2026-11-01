package tools.confido.payloads.responses

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import tools.confido.rooms.Room
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.users.UserPresentation

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
    /** effectiveSchedule.score - when the prediction was frozen for scoring;
     *  null = no score time set, the user's latest prediction was scored */
    val scoreTime: Instant?,
    /** When the question state changed to RESOLVED (null if unknown) */
    val resolveTime: Instant?,
    /** Other rooms (e.g. group rooms) that also include this question via effectiveQuestions.
     *  The primary room (roomRef) is the room where the question actually lives. */
    val additionalRooms: List<Ref<Room>> = emptyList(),
)

@Serializable
data class MyScoresResponse(
    val items: List<MyScoreItem>,
    val totalScore: Double,
    /** Per-room rank info keyed by room ID.
     *  Includes every room where the user has scores and scoring is enabled. */
    val roomRanks: Map<String, MyRankResponse> = emptyMap(),
)

/** A single entry in the room scoreboard (leaderboard). */
@Serializable
data class ScoreboardEntry(
    val user: UserPresentation,
    val score: Double,
    val questionsAnswered: Int,
)

/** Response for the user's rank within a specific room's scoreboard. */
@Serializable
data class MyRankResponse(
    /** 1-based rank in the room scoreboard. */
    val rank: Int,
    /** Total number of participants with scores in this room. */
    val totalParticipants: Int,
    /** The user's total score in this room. */
    val myScore: Double,
)
