package extensions

import tools.confido.extensions.*
import tools.confido.question.QuestionEDT

val QuestionGroupsKey = ExtensionDataKeyWithDefault<Set<String>>("question_groups", emptySet())

open class QuestionGroupsExtension: Extension {
    override val extensionId = "question_groups"
    override fun registerEdtKeys(edt: ExtensionDataType) {
        when (edt.name) {
            "QuestionEDT" -> {
                edt.add(QuestionGroupsKey)
            }
        }
    }
}
