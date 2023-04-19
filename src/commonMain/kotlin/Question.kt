package tools.confido.question

import kotlinx.serialization.*
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.refs.HasId
import tools.confido.refs.ImmediateDerefEntity
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.spaces.*
import tools.confido.state.globalState
import tools.confido.utils.HasUrlPrefix
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

interface Terminology {
    abstract val name: String

    val term get() = name.lowercase()
    val aTerm get() = "a $term"
    val plural get() = "${term}s"
}
enum class PredictionTerminology: Terminology {
    PREDICTION,
    ANSWER,
    ESTIMATE;

    override val aTerm get() = when (this) {
        PredictionTerminology.ANSWER, PredictionTerminology.ESTIMATE -> "an $term"
        else -> "a $term"
    }
}

enum class GroupTerminology: Terminology {
    GROUP,
    CROWD,
    TEAM;
}

@Serializable
data class Question(
    @SerialName("_id")
    override val id: String,
    val name: String,
    val answerSpace: Space,
    val description: String = "",
    val predictionTerminology: PredictionTerminology = PredictionTerminology.PREDICTION,
    val groupTerminology: GroupTerminology = GroupTerminology.GROUP,
    val visible: Boolean = true,
    /** Submitting predictions allowed */
    val open: Boolean = true,
    val groupPredVisible: Boolean = false,
    val resolutionVisible: Boolean = false,
    val resolution: Value? = null,
    val allowComments: Boolean = true,
    val sensitive: Boolean = false,
) : ImmediateDerefEntity, HasUrlPrefix {
    init {
        if (resolution != null) {
            require(resolution.space == answerSpace)
        }
    }

    val resolved : Boolean get() = resolution != null
    val numPredictions get() = globalState.predictionCount[ref] ?: 0
    val numPredictors get() = globalState.predictorCount[ref] ?: 0

    override val urlPrefix get() = urlPrefix(id)
    companion object {
        fun urlPrefix(id: String) = "/questions/$id"
    }
}

