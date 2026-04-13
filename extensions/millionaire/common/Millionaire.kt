package extensions.millionaire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import normalizedBrier
import rooms.Room
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.Extension
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.spaces.BinaryValue
import tools.confido.spaces.Value
import tools.confido.state.FlowAction
import tools.confido.state.PresenterView
import tools.confido.utils.ellipsize
import tools.confido.utils.mapDeref
import tools.confido.utils.toInt
import kotlin.math.pow

// --- Mode & Configuration ---

@Serializable
enum class MillionaireMode { MULTIPLICATIVE, LADDER }

@Serializable
sealed class TimerConfig {
    @Serializable @SerialName("none")
    object None : TimerConfig()
    @Serializable @SerialName("fixed")
    data class Fixed(val seconds: Int) : TimerConfig()       // auto-start when question opens
    @Serializable @SerialName("countdown")
    data class Countdown(val seconds: Int) : TimerConfig()   // manually triggered final countdown
}

val DEFAULT_PRIZE_LADDER = listOf(
    100, 200, 300, 500, 1_000,
    2_000, 4_000, 8_000, 16_000, 32_000,
    64_000, 125_000, 250_000, 500_000, 1_000_000,
)

/** Format a prize amount for display, e.g. 1000 -> "€1,000", 125000 -> "€125,000" */
fun formatPrize(amount: Int): String {
    val s = amount.toString()
    val result = StringBuilder()
    for (i in s.indices) {
        if (i > 0 && (s.length - i) % 3 == 0) result.append(',')
        result.append(s[i])
    }
    return "€$result"
}

// --- Timer State (shared between server and client) ---

@Serializable
data class TimerState(
    val startedAt: Double,   // epoch millis
    val durationMs: Int,
    val running: Boolean,
)

// --- Game State ---

@Serializable
enum class MillionaireStateType { BEFORE_START, ASKING, RESOLVED }

@Serializable
data class MillionaireState(
    val type: MillionaireStateType,
    val curIndex: Int,
    val curQuestion: Question?,
)

// --- Flow Actions ---

/**
 * Expandable meta-action representing a complete millionaire game.
 * At presentation start, this expands into a sequence of [MillionaireSetStateAction]
 * items that step through each question (open, show, close, scoreboard).
 * All configuration that was previously in environment variables lives here.
 */
@Serializable
@SerialName("millionaireGame")
data class MillionaireGameAction(
    val tag: String = "mil",
    val mode: MillionaireMode = MillionaireMode.LADDER,
    val timer: TimerConfig = TimerConfig.None,
    val prizeLadder: List<Int> = DEFAULT_PRIZE_LADDER,
    val showScoreboardAfterEach: Boolean = true,
) : FlowAction() {
    override fun describe() = "Millionaire game (tag: $tag)"

    fun prizeLadderForCount(n: Int): List<Int> = prizeLadder.take(n)
}

/**
 * Concrete per-step action produced by expanding [MillionaireGameAction].
 * Each instance transitions the game to a specific state (e.g. open question N,
 * close question N) and optionally sets the presenter view.
 */
@Serializable
@SerialName("millionaireSetState")
data class MillionaireSetStateAction(
    val stateType: MillionaireStateType,
    val curIndex: Int,
    val tag: String,
    /** Short question text for display in the runner (populated during expansion). */
    val questionName: String = "",
    override val presenterView: PresenterView? = null,
) : FlowAction() {
    override fun describe(): String {
        val qLabel = if (questionName.isNotEmpty()) ": ${questionName.ellipsize(50)}" else ""
        return when (stateType) {
            MillionaireStateType.ASKING -> "Open Q${curIndex + 1}$qLabel"
            MillionaireStateType.RESOLVED -> "Close Q${curIndex + 1}$qLabel"
            MillionaireStateType.BEFORE_START -> "Reset game"
        }
    }
}

/**
 * Action that starts the countdown timer for the current question.
 * Generated in the expansion when [TimerConfig.Countdown] is configured,
 * giving the presenter a manual "start timer" step.
 */
@Serializable
@SerialName("millionaireStartTimer")
data class MillionaireStartTimerAction(
    val seconds: Int,
) : FlowAction() {
    override fun describe() = "Start countdown (${seconds}s)"
}

// --- Presenter Views ---

@Serializable
data class MillionaireScoreboardPV(val room: Ref<Room>) : PresenterView() {
    override fun describe() = "Millionaire scoreboard"
}

@Serializable
data class MillionaireLadderQuestionPV(val room: Ref<Room>) : PresenterView() {
    override fun describe() = "Millionaire ladder question view"
}

// --- Scoring ---

abstract class MillionaireExt : Extension {
    override val extensionId = "millionaire"

    override fun registerPresenterViews(builder: PolymorphicModuleBuilder<PresenterView>) {
        builder.subclass(MillionaireScoreboardPV::class)
        builder.subclass(MillionaireLadderQuestionPV::class)
    }

    override fun registerFlowActions(builder: PolymorphicModuleBuilder<FlowAction>) {
        builder.subclass(MillionaireGameAction::class)
        builder.subclass(MillionaireSetStateAction::class)
        builder.subclass(MillionaireStartTimerAction::class)
    }

    // --- Multiplicative mode scoring (original) ---

    val INITIAL_SCORE = 1000.0

    fun computeScoreMultiplicative(pred: Double, res: Boolean) =
        if (pred == 0.5) 1.0
        else (4.0 / 3.0) * (1 - (pred - res.toInt()).pow(2))

    fun computeScoreMultiplicative(pred: Prediction?, res: Value): Double? {
        return computeScoreMultiplicative(
            (pred?.dist as? BinaryDistribution)?.yesProb ?: return null,
            (res as BinaryValue).value,
        )
    }

    // --- Ladder mode scoring (additive, based on normalizedBrier) ---

    /** Returns a dollar amount in range [-3*prizeValue .. +prizeValue] */
    fun computeScoreLadder(pred: Double, res: Boolean, prizeValue: Int): Double =
        prizeValue * normalizedBrier(pred, res)

    fun computeScoreLadder(pred: Prediction?, res: Value, prizeValue: Int): Double? {
        return computeScoreLadder(
            (pred?.dist as? BinaryDistribution)?.yesProb ?: return null,
            (res as BinaryValue).value,
            prizeValue,
        )
    }

    // --- Backward-compatible aliases ---

    fun computeScore(pred: Double, res: Boolean) = computeScoreMultiplicative(pred, res)
    fun computeScore(pred: Prediction?, res: Value) = computeScoreMultiplicative(pred, res)

    // --- Question helpers ---

    fun questions(room: Room, tag: String) = room.questions.reversed().mapDeref().filter {
        tag in it.tags
    }

    fun getState(room: Room, tag: String): MillionaireState {
        val qs = questions(room, tag)
        if (qs.isEmpty()) return MillionaireState(MillionaireStateType.BEFORE_START, -1, null)
        qs.forEachIndexed { idx, q ->
            if (q.state == QuestionState.OPEN) {
                return MillionaireState(MillionaireStateType.ASKING, idx, q)
            } else if (q.state != QuestionState.RESOLVED) {
                if (idx > 0) return MillionaireState(MillionaireStateType.RESOLVED, idx - 1, qs[idx - 1])
                else return MillionaireState(MillionaireStateType.BEFORE_START, -1, null)
            }
        }
        return MillionaireState(MillionaireStateType.RESOLVED, qs.size - 1, qs.last())
    }
}
