package tools.confido.state

import tools.confido.question.Question
import kotlinx.serialization.Serializable

@Serializable
data class AppState(
    val questions: List<Question>,
    val session: UserSession,
)