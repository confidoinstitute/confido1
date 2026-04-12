package extensions.millionaire

import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import normalizedBrier
import rooms.Room
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.Extension
import tools.confido.extensions.get
import extensions.question_groups.QuestionGroupsKey
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.spaces.BinaryValue
import tools.confido.spaces.Value
import tools.confido.state.EmptyPV
import tools.confido.state.PresenterView
import tools.confido.state.QuestionPV
import tools.confido.state.appConfig
import tools.confido.utils.mapDeref
import tools.confido.utils.toInt
import kotlin.math.pow

// --- Mode & Configuration ---

@Serializable
enum class MillionaireMode { MULTIPLICATIVE, LADDER }

sealed class TimerConfig {
    object None : TimerConfig()
    data class Fixed(val seconds: Int) : TimerConfig()       // auto-start when question opens
    data class Countdown(val seconds: Int) : TimerConfig()   // manually triggered final countdown
}

val DEFAULT_PRIZE_LADDER = listOf(
    100, 200, 300, 500, 1_000,
    2_000, 4_000, 8_000, 16_000, 32_000,
    64_000, 125_000, 250_000, 500_000, 1_000_000,
)

object MillionaireConfig {
    val mode: MillionaireMode get() = when (appConfig.extraData["millionaire_mode"]) {
        "ladder" -> MillionaireMode.LADDER
        else -> MillionaireMode.MULTIPLICATIVE
    }

    val timerConfig: TimerConfig get() {
        val raw = appConfig.extraData["millionaire_timer"] ?: return TimerConfig.None
        return when {
            raw.startsWith("fixed:") -> TimerConfig.Fixed(raw.removePrefix("fixed:").toIntOrNull() ?: return TimerConfig.None)
            raw.startsWith("countdown:") -> TimerConfig.Countdown(raw.removePrefix("countdown:").toIntOrNull() ?: return TimerConfig.None)
            else -> TimerConfig.None
        }
    }

    val prizeLadder: List<Int> get() {
        val custom = appConfig.extraData["millionaire_ladder"]
        return if (custom != null) custom.split(",").map { it.trim().toInt() }
        else DEFAULT_PRIZE_LADDER
    }

    fun prizeLadderForCount(n: Int): List<Int> = prizeLadder.take(n)

    /** Format a dollar amount for display, e.g. 1000 -> "$1,000", 125000 -> "$125,000" */
    fun formatPrize(amount: Int): String {
        val s = amount.toString()
        val result = StringBuilder()
        for (i in s.indices) {
            if (i > 0 && (s.length - i) % 3 == 0) result.append(',')
            result.append(s[i])
        }
        return "$$result"
    }
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
) {
    fun presenterView(room: Room): PresenterView =
        if (type == MillionaireStateType.ASKING && curQuestion != null) {
            when (MillionaireConfig.mode) {
                MillionaireMode.LADDER -> MillionaireLadderQuestionPV(room.ref)
                MillionaireMode.MULTIPLICATIVE -> QuestionPV(curQuestion.ref)
            }
        } else if (type == MillionaireStateType.RESOLVED) {
            MillionaireScoreboardPV(room.ref)
        } else {
            EmptyPV
        }
}

// --- Presenter Views ---

@Serializable
data class MillionairePV(val room: Ref<Room>) : PresenterView() {
    override fun describe() = "Dynamically updating millionaire view"
}

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
    val MIL_GROUP = "mil"

    override fun registerPresenterViews(builder: PolymorphicModuleBuilder<PresenterView>) {
        builder.subclass(MillionaireScoreboardPV::class)
        builder.subclass(MillionairePV::class)
        builder.subclass(MillionaireLadderQuestionPV::class)
    }

    // --- Multiplicative mode scoring (original) ---

    val INITIAL_SCORE = 1000.0

    fun computeScoreMultiplicative(pred: Double, res: Boolean) =
        // ensure that we return _exactly_ 1.0 for pred==0.5 (the formula also equals 1.0 but not sure about
        // float roundoff errors)
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

    // --- Backward-compatible aliases used by existing code ---

    fun computeScore(pred: Double, res: Boolean) = computeScoreMultiplicative(pred, res)
    fun computeScore(pred: Prediction?, res: Value) = computeScoreMultiplicative(pred, res)

    // --- Question helpers ---

    fun questions(room: Room) = room.questions.reversed().mapDeref().filter {
        it.extensionData[QuestionGroupsKey].contains(MIL_GROUP)
    }

    fun getState(room: Room): MillionaireState {
        val qs = questions(room)
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
