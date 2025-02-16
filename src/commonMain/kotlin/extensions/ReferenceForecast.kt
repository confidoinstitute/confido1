package extensions

import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import tools.confido.extensions.*
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.state.PresenterView

val ReferenceForcastKey = ExtensionDataKeyWithDefault<Double?>("reference_forecast", null)

@Serializable
data class PredictionWithUser(
    val nickname: String,
    val probability: Double,
    val isReference: Boolean = false
)

@Serializable
data class ReferenceForcastPV(
    val question: Ref<Question>,
): PresenterView() {
    override fun describe() = "Individual predictions with reference forecast"
}

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
        builder.subclass(ReferenceForcastPV::class)
    }
}
