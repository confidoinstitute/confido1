package tools.confido.question

import kotlinx.serialization.*
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.spaces.*

@Serializable
data class Prediction(
    val ts: Int,
    val dist: ProbabilityDistribution,
)

@Serializable
data class Question(
    @SerialName("_id")
    val id: String,
    val name: String,
    val answerSpace: Space,
    var visible: Boolean = true,
    var enabled: Boolean = true,
    var predictionsVisible: Boolean = false,
    var resolved: Boolean = false,
)

