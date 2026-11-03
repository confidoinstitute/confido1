package tools.confido.extensions.prediction_showcase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import tools.confido.distributions.*
import tools.confido.extensions.*
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.state.PresenterView
import tools.confido.rooms.Room
import tools.confido.extensions.shared.ValueWithUser
import kotlinx.serialization.serializer
import tools.confido.state.Topics
import tools.confido.utils.TopicSpec

@Serializable
@SerialName("prediction_showcase")
data class PredictionShowcasePV(
    val question: Ref<Question>
): PresenterView() {
    override fun describe() = "Individual predictions with group prediction"
    override fun questionRefs() = listOf(question)
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) = copy(question = f(question))
    override fun configProblem() = if (question.id.isEmpty()) "No question selected" else null
}

@Serializable
@SerialName("group_pred_compare")
data class GroupPredictionComparePV(
    val room: Ref<Room>,
    val questionGroup: String? = null
): PresenterView() {
    override fun describe() = "Compare group predictions across questions"
    // Backfilled by the flow editor's roomSubject; guards hand-crafted payloads.
    override fun configProblem() = if (room.id.isEmpty()) "No room set" else null
}

open class PredictionShowcaseExtension: Extension {
    override val extensionId = "prediction_showcase"

    override fun registerPresenterViews(builder: PolymorphicModuleBuilder<PresenterView>) {
        builder.subclass(PredictionShowcasePV::class)
        builder.subclass(GroupPredictionComparePV::class)
    }
}

// Topic owned by this extension (see tools.confido.state.Topics for the scheme).
val showcasePredictionsTopic = TopicSpec(Topics.questionPath / "ext/prediction_showcase/predictions", serializer<List<ValueWithUser>>())
