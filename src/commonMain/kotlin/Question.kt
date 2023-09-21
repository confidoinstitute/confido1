package tools.confido.question

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
data class Prediction(
    @SerialName("_id")
    override val id: String = "",
    val ts: Int,
    val question: Ref<Question>,
    val user: Ref<User>?,
    val dist: ProbabilityDistribution,
) : HasId

interface Terminology {
    abstract val name: String

    val term get() = name.lowercase()
    val aTerm get() = "a $term"
    val plural get() = "${term}s"
}

enum class PredictionTerminology : Terminology {
    PREDICTION,
    ANSWER,
    ESTIMATE;

    override val aTerm
        get() = when (this) {
            PredictionTerminology.ANSWER, PredictionTerminology.ESTIMATE -> "an $term"
            else -> "a $term"
        }
}

enum class GroupTerminology : Terminology {
    GROUP,
    CROWD,
    TEAM;
}

enum class GroupPredictionVisibility : Terminology {
    EVERYONE,
    ANSWERED,
    MODERATOR_ONLY;

    val groupPredVisible
        get() = when (this) {
            EVERYONE -> true
            ANSWERED -> true
            MODERATOR_ONLY -> false
        }

    val groupPredRequirePrediction
        get() = when (this) {
            EVERYONE -> false
            ANSWERED -> true
            MODERATOR_ONLY -> false
        }

    companion object {
        internal fun fromBooleans(
            groupPredVisible: Boolean, groupPredRequirePrediction: Boolean
        ): GroupPredictionVisibility = when (groupPredVisible) {
            true -> when (groupPredRequirePrediction) {
                true -> ANSWERED
                false -> EVERYONE
            }

            false -> MODERATOR_ONLY
        }
    }

    /**
     * Currently, this enum wraps two different booleans within the entity.
     * This is a convenience function that sets both of them.
     */
    fun apply(question: Question): Question {
        return question.copy(
            groupPredVisible = groupPredVisible,
            groupPredRequirePrediction = groupPredRequirePrediction
        )
    }
}

@Serializable
enum class QuestionState {
    OPEN,
    CLOSED,
    RESOLVED,
    ANNULLED,
}

@Serializable
data class QuestionStateChange(
    val newState: QuestionState,
    val at: Instant,
    val user: Ref<User>?,
)

@Serializable
data class QuestionSchedule(
    val open: Instant? = null,
    val score: Instant? = null,
    val close: Instant? = null,
    val resolve: Instant? = null,
) {
    fun identify() = "${open}:${score}:${close}:${resolve}"
}

@Serializable
data class QuestionScheduleStatus(
    val openDone: Boolean = false,
    val closeDone: Boolean = false,
    val resolveDone: Boolean = false,
) {
    constructor(sched: QuestionSchedule) : this(
        sched.open != null && sched.open < Clock.System.now(),
        sched.close != null && sched.close < Clock.System.now(),
        sched.resolve != null && sched.resolve < Clock.System.now(),
    )
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
    /**
     * This value should be interpreted through [groupPredictionVisibility]
     * which also includes the effects of [groupPredRequirePrediction].
     */
    @Deprecated("Use groupPredictionVisibility instead.")
    val groupPredVisible: Boolean = false,
    /**
     * This value should be interpreted through [groupPredictionVisibility]
     * which also includes the effects of [groupPredVisible].
     */
    @Deprecated("Use groupPredictionVisibility instead.")
    val groupPredRequirePrediction: Boolean = false,
    val resolutionVisible: Boolean = false,
    val resolution: Value? = null,
    val allowComments: Boolean = true,
    val annulled: Boolean = false,
    val sensitive: Boolean = false,
    val author: Ref<User>? = null,
    val stateHistory: List<QuestionStateChange> = emptyList(),

    // null = inherit default schedule from room
    // The default value is purposefully NOT null because we do not want existing questions
    // (created before this feature was introduced) to ex-post start inheriting room schedule.
    val schedule: QuestionSchedule? = QuestionSchedule(),
    val scheduleStatus: QuestionScheduleStatus = QuestionScheduleStatus(),
) : ImmediateDerefEntity, HasUrlPrefix {
    init {
        if (resolution != null) {
            require(resolution.space == answerSpace)
        }
    }

    val resolved: Boolean get() = resolution != null
    val numPredictions get() = globalState.predictionCount[ref] ?: 0
    val numPredictors get() = globalState.predictorCount[ref] ?: 0

    fun withState(questionState: QuestionState): Question {
        return when (questionState) {
            QuestionState.OPEN -> {
                this.copy(open = true, resolutionVisible = false, annulled = false)
            }

            QuestionState.CLOSED -> {
                this.copy(open = false, resolutionVisible = false, annulled = false)
            }

            QuestionState.RESOLVED -> {
                this.copy(open = false, resolutionVisible = true, annulled = false)
            }

            QuestionState.ANNULLED -> {
                this.copy(open = false, resolutionVisible = false, annulled = true)
            }
        }
    }

    val state: QuestionState
        get() {
            if (annulled)
                return QuestionState.ANNULLED
            if (resolved && resolutionVisible)
                return QuestionState.RESOLVED
            if (!open)
                return QuestionState.CLOSED
            return QuestionState.OPEN
        }

    @Suppress("DEPRECATION")
    val groupPredictionVisibility: GroupPredictionVisibility
        get() = GroupPredictionVisibility.fromBooleans(groupPredVisible, groupPredRequirePrediction)

    override val urlPrefix get() = urlPrefix(id)

    val room get() = globalState.rooms.values.firstOrNull { ref in it.questions }
    val effectiveSchedule = schedule ?: room?.defaultSchedule ?: QuestionSchedule()
    companion object {
        fun urlPrefix(id: String) = "/questions/$id"
    }
}

