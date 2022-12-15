package payloads.requests

import kotlinx.serialization.Serializable
import tools.confido.question.Question
import tools.confido.refs.Ref

@Serializable
data class ReorderQuestions(
    val newOrder: List<Ref<Question>>,
)
