package payloads.requests

import kotlinx.serialization.Serializable
import tools.confido.question.Question

@Serializable
enum class EditQuestionFieldType {
    VISIBLE,
    ENABLED,
    PREDICTIONS_VISIBLE,
    RESOLVED,
}

@Serializable
sealed class EditQuestion ()

@Serializable
data class EditQuestionField(
    val fieldType: EditQuestionFieldType,
    val value: Boolean,
) : EditQuestion()

@kotlinx.serialization.Serializable
data class EditQuestionComplete(
    val question: Question,
    val room: String,
) : EditQuestion()