package extensions.conditional_baseline

import tools.confido.extensions.Extension
import tools.confido.extensions.ExtensionDataKey
import tools.confido.extensions.ExtensionDataType
import tools.confido.extensions.add
import tools.confido.question.Question
import tools.confido.refs.Ref

abstract class ConditionalBaselineExtension : Extension {
    val BaselineQuestionKey = ExtensionDataKey<Ref<Question>>("baseline_question")
    override val extensionId = "conditional_baseline"
    override fun registerEdtKeys(edt: ExtensionDataType) {
        when (edt.name) {
            "QuestionEDT" -> edt.add(BaselineQuestionKey)
        }
    }
}
