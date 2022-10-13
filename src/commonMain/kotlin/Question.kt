package tools.confido.question

import kotlinx.serialization.*
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.refs.HasId
import tools.confido.refs.ImmediateDerefEntity
import tools.confido.refs.Ref
import tools.confido.spaces.*
import users.User

@Serializable
data class Prediction (
    @SerialName("_id")
    override val id: String = "",
    val ts: Int,
    val question: Ref<Question>,
    val user: Ref<User>?,
    val dist: ProbabilityDistribution,
): HasId

@Serializable
data class Question(
    @SerialName("_id")
    override val id: String,
    val name: String,
    val answerSpace: Space,
    val description: String = "",
    val visible: Boolean = true,
    val open: Boolean = true, // submitting predictions allowed
    val groupPredVisible: Boolean = false,
    val resolutionVisible: Boolean = false,
    val resolution: Value? = null,
) : ImmediateDerefEntity {
    init {
        if (resolution != null) {
            require(resolution.space == answerSpace)
        }
    }

    val resolved : Boolean get() = resolution != null
}

