package extensions.reference_forecast

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import tools.confido.extensions.*
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.state.PresenterView
import rooms.Room
import kotlinx.serialization.serializer
import tools.confido.state.Topics
import tools.confido.utils.TopicSpec

val ReferenceForcastKey = ExtensionDataKeyWithDefault<Double?>("reference_forecast", null)

@Serializable
@SerialName("reference_forecast_scoreboard")
data class ReferenceForcastScoreboardPV(
    val room: Ref<Room>,
): PresenterView() {
    override fun describe() = "Reference forecast scoreboard"
}

@Serializable
data class UserScore(
    val nickname: String,
    val score: Double,
    val numQuestions: Int,
    val isSpecial: Boolean = false,
)

open class ReferenceForecastExtension: Extension {
    override val extensionId = "reference_forecast"
    override fun registerEdtKeys(edt: ExtensionDataType) {
        when (edt.name) {
            "QuestionEDT" -> {
                edt.add(ReferenceForcastKey)
            }
        }
    }

    override fun registerPresenterViews(builder: PolymorphicModuleBuilder<PresenterView>) {
        builder.subclass(ReferenceForcastScoreboardPV::class)
    }
}

// Topic owned by this extension (see tools.confido.state.Topics for the scheme).
val referenceScoreboardTopic = TopicSpec(Topics.roomPath / "ext/reference_forecast/scoreboard", serializer<List<UserScore>>())
