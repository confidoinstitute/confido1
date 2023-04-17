package payloads.requests

import kotlinx.serialization.Serializable
import tools.confido.question.Question
import tools.confido.refs.Ref

/**
 * Set the room's questions to a given ordering. Refs of questions not in room are ignored and questions not in the ordering are appended at the end.
 */
@Serializable
data class ReorderQuestions(
    val newOrder: List<Ref<Question>>,
)
