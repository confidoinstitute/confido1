package tools.confido.question

import tools.confido.refs.Entity
import kotlinx.serialization.*
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.refs.ImmediateDerefEntity
import tools.confido.refs.Ref
import tools.confido.spaces.*
import tools.confido.utils.generateId
import tools.confido.utils.randomString
import users.User

@Serializable
data class Prediction(
    @SerialName("_id")
    val id: String = "",
    val ts: Int,
    val question: Ref<Question>,
    val user: Ref<User>?,
    val dist: ProbabilityDistribution,
)

@Serializable
data class Question(
    @SerialName("_id")
    override val id: String,
    val name: String,
    val answerSpace: Space,
    var visible: Boolean = true,
    var enabled: Boolean = true,
    var predictionsVisible: Boolean = false,
    var resolved: Boolean = false,
) : ImmediateDerefEntity

