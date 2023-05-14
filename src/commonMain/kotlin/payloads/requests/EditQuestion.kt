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

/**
 * Sum data class for information about a question (defined by POST URL) edit request
 */
@Serializable
sealed class EditQuestion ()

/**
 * Quickly change a binary option [fieldType] of a question to a given [value].
 */
@Serializable
data class EditQuestionFlag(
    val fieldType: EditQuestionFieldType,
    val value: Boolean,
) : EditQuestion()


/**
 * Set the question resolution to a given [value].
 */
@Serializable
data class EditQuestionResolution(
    val resolution: Value,
)

/**
 * Edit question as a whole, some of the fields may be ignored.
 */
@Serializable
data class EditQuestionComplete(
    val question: Question,
) : EditQuestion()
