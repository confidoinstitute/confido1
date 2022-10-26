package payloads.requests

import kotlinx.serialization.Serializable
import tools.confido.question.Question
import tools.confido.spaces.Value

@Serializable
enum class EditQuestionFieldType {
    VISIBLE,
    OPEN,
    GROUP_PRED_VISIBLE,
    RESOLUTION_VISIBLE,
}

@Serializable
sealed class EditQuestion ()

@Serializable
data class EditQuestionFlag(
    val fieldType: EditQuestionFieldType,
    val value: Boolean,
) : EditQuestion()

@Serializable
data class EditQuestionResolution(
    val resolution: Value,
)

@Serializable
data class EditQuestionComplete(
    val question: Question,
) : EditQuestion()
