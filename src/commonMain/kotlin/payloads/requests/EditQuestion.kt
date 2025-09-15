package payloads.requests

import kotlinx.serialization.Serializable
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
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
 * Marks edit types that make sense to apply to multiple questions in a batch
 */
@Serializable
sealed class EditQuestionCanMulti : EditQuestion()

/**
 * Quickly change a binary option [fieldType] of a question to a given [value].
 */
@Serializable
data class EditQuestionFlag(
    val fieldType: EditQuestionFieldType,
    val value: Boolean,
) : EditQuestionCanMulti()


/**
 * Set the question resolution to a given [value].
 */
@Serializable
data class EditQuestionResolution(
    val resolution: Value,
) : EditQuestionCanMulti()

/**
 * Edit question as a whole, some of the fields may be ignored.
 */
@Serializable
data class EditQuestionComplete(
    val question: Question,
) : EditQuestion()

@Serializable
data class EditQuestionState(
    val newState: QuestionState
): EditQuestionCanMulti()

@Serializable
object EditQuestionClearAnswers: EditQuestionCanMulti()

@Serializable
data class EditQuestionMulti(
    val questions: Set<Ref<Question>>,
    val edit: EditQuestionCanMulti,
)