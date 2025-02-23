package extensions

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import tools.confido.extensions.Extension
import tools.confido.extensions.ExtensionDataKeyWithDefault
import tools.confido.extensions.ExtensionDataType
import tools.confido.extensions.add
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.state.PresenterView

@Serializable
data class UpdateScatterPlotPV(
    val question: Ref<Question>,
    val time1: Instant?,
    val time2: Instant?,
): PresenterView() {
    override fun describe() = "Update scatter plot"
}

val UpdateReferenceQuestionKey = ExtensionDataKeyWithDefault<Ref<Question>?>("update_reference_question", null)

interface UpdateScatterPlotExt: Extension {
    override val extensionId get() = "update_scatter_plot"
    override fun registerPresenterViews(builder: PolymorphicModuleBuilder<PresenterView>) {
        builder.subclass(UpdateScatterPlotPV::class)
    }
    override fun registerEdtKeys(edt: ExtensionDataType) {
        when (edt.name) {
            "QuestionEDT" -> {
                edt.add(UpdateReferenceQuestionKey)
            }
        }
    }
}