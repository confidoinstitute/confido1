package extensions

import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import rooms.Room
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.Extension
import tools.confido.extensions.get
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
import tools.confido.utils.mapDeref
import tools.confido.utils.toInt
import kotlin.math.pow

@Serializable
enum class MillionaireStateType { BEFORE_START, ASKING, RESOLVED }
@Serializable
data class MillionaireState(
    val type: MillionaireStateType,
    val curIndex: Int,
    val curQuestion: Question?,
) {
    fun presenterView(room: Room) =
        if (type == MillionaireStateType.ASKING && curQuestion != null)
            QuestionPV(curQuestion.ref)
        else if (type == MillionaireStateType.RESOLVED) MillionaireScoreboardPV(room.ref)
        else EmptyPV
}

@Serializable
data class MillionaireScoreboardPV(val room: Ref<Room>) : PresenterView() {
    override fun describe() = "Millionaire scoreboard"
}

abstract class MillionaireExt : Extension {
    override val extensionId = "millionaire"
    val MIL_GROUP = "mil"
    override fun registerPresenterViews(builder: PolymorphicModuleBuilder<PresenterView>) {
        builder.subclass(MillionaireScoreboardPV::class)
    }
    val INITIAL_SCORE = 1000.0
    fun computeScore(pred: Double, res: Boolean) =
    // ensure that we return _exactly_ 1.0 for pred==0.5 (the formula also equals 1.0 but not sure about
        // float roundoff errors)
        if (pred == 0.5) 1.0
        else (4.0/3.0)  * (1 - (pred - res.toInt()).pow(2))

    fun computeScore(pred: Prediction?, res: Value): Double? {
        return computeScore((pred?.dist as? BinaryDistribution)?.yesProb ?: return null, (res as BinaryValue).value)
    }
    fun questions(room: Room) = room.questions.reversed().mapDeref().filter {
            it.extensionData[QuestionGroupsKey].contains(MIL_GROUP)
        }
    fun getState(room: Room): MillionaireState {
        val qs = questions(room)
        if (qs.isEmpty()) return MillionaireState(MillionaireStateType.BEFORE_START, -1, null)
        val idx = qs.forEachIndexed { idx,q->
            if (q.state == QuestionState.OPEN) {
                return MillionaireState(MillionaireStateType.ASKING, idx, q)
            } else if (q.state != QuestionState.RESOLVED) {
                if (idx > 0) return MillionaireState(MillionaireStateType.RESOLVED, idx-1, qs[idx-1])
                else return MillionaireState(MillionaireStateType.BEFORE_START, -1, null)
            }
        }
        return MillionaireState(MillionaireStateType.RESOLVED, qs.size-1, qs.last())
    }
}