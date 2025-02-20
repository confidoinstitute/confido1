package extensions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import tools.confido.distributions.*
import tools.confido.extensions.*
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.state.PresenterView

@Serializable
@SerialName("prediction_showcase")
data class PredictionShowcasePV(
    val question: Ref<Question>
): PresenterView() {
    override fun describe() = "Individual predictions with group prediction"
}

open class PredictionShowcaseExtension: Extension {
    override val extensionId = "prediction_showcase"

    override fun registerPresenterViews(builder: PolymorphicModuleBuilder<PresenterView>) {
        builder.subclass(PredictionShowcasePV::class)
    }
}
