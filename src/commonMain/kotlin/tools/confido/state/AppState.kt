package tools.confido.state

import tools.confido.question.Question
import kotlinx.serialization.Serializable
import tools.confido.question.Prediction

@Serializable
data class AppState(
    val questions: Map<String, Question>,
    val userPredictions: Map<String, Prediction>,
    val groupDistributions: Map<String, List<Double>>,
    val session: UserSession,
    val isAdmin: Boolean = false,
)