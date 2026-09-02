package tools.confido.question

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.*
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.extensions.ExtensionData
import tools.confido.extensions.ExtensionDataSerializer
import tools.confido.extensions.ExtensionDataType
import tools.confido.refs.HasId
import tools.confido.refs.ImmediateDerefEntity
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.spaces.*
import tools.confido.rooms.Room
import tools.confido.rooms.TimelineMark
import tools.confido.utils.HasUrlPrefix
import tools.confido.users.User

import tools.confido.rooms.AttributionPolicy

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

    /** Verb form for prompts like "be the first to predict". */
    val verb
        get() = when (this) {
            PREDICTION -> "predict"
            ANSWER -> "answer"
            ESTIMATE -> "estimate"
        }
}

enum class GroupTerminology : Terminology {
    GROUP,
    CROWD,
    TEAM;
}

@Serializable
enum class CommentVisibility {
    EVERYONE,
    ANSWERED,
    MODERATOR_ONLY;
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
    DRAFT,
    OPEN,
    CLOSED,
    RESOLVED,
    @SerialName("ANNULLED") //Backward compat
    CANCELLED,
}

val QuestionState.pastVerb get() = when (this) {
    QuestionState.DRAFT -> "draft since"
    QuestionState.OPEN -> "opened"
    QuestionState.CLOSED -> "closed"
    QuestionState.RESOLVED -> "resolved"
    QuestionState.CANCELLED -> "cancelled"
}
val QuestionState.futureVerb get() = when (this) {
    QuestionState.DRAFT -> "" // not applicable
    QuestionState.OPEN -> "opens"
    QuestionState.CLOSED -> "closes"
    QuestionState.RESOLVED -> "resolves"
    QuestionState.CANCELLED -> "" // not applicable
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

val QuestionEDT = ExtensionDataType("QuestionEDT")
class QuestionEDTSerializer: ExtensionDataSerializer(QuestionEDT)

enum class ExtremeProbabilityMode {
    NORMAL,
    EXTREME_LOW,
    EXTREME_HIGH
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
    val commentVisibility: CommentVisibility = CommentVisibility.EVERYONE,
    val annulled: Boolean = false,
    // null = inherit the room's default attribution policy (interpret via
    // [effectiveAttributionIn], same pattern as [schedule]/[effectiveScheduleIn]).
    val attribution: AttributionPolicy? = null,
    /** Promise floor: the strictest ([strictestOf]) effective policy any of this
     *  question's activity (predictions, comments) was authored under. Maintained
     *  server-side at the activity-insert choke points; the ratchet refuses any
     *  edit whose effective policy would reveal more than this. Null = no promise
     *  yet (or only OPEN activity — an OPEN floor never binds). Never lowers,
     *  even if activity is later deleted. */
    val attributionFloor: AttributionPolicy? = null,
    val author: Ref<User>? = null,
    val stateHistory: List<QuestionStateChange> = emptyList(),
    /** Question-scoped timeline markers; room-wide ones live on [tools.confido.rooms.Room.timelineMarks]. */
    val timelineMarks: List<TimelineMark> = emptyList(),

    // null = inherit default schedule from room
    // The default value is purposefully NOT null because we do not want existing questions
    // (created before this feature was introduced) to ex-post start inheriting room schedule.
    val schedule: QuestionSchedule? = QuestionSchedule(),
    val scheduleStatus: QuestionScheduleStatus = QuestionScheduleStatus(),
    @Serializable(with = QuestionEDTSerializer::class)
    val extensionData: ExtensionData = ExtensionData(QuestionEDT),
    /** Tags for organizing and filtering questions within a room. */
    val tags: Set<String> = emptySet(),
    /** Optional external identifier for matching during CSV import. */
    val externalId: String? = null,
) : ImmediateDerefEntity, HasUrlPrefix {
    init {
        if (resolution != null) {
            require(resolution.space == answerSpace)
        }
    }

    val resolved: Boolean get() = resolution != null

    fun withState(questionState: QuestionState): Question {
        return when (questionState) {
            QuestionState.DRAFT -> {
                this.copy(open = false, resolutionVisible = false, annulled = false, visible = false)
            }
            QuestionState.OPEN -> {
                this.copy(open = true, resolutionVisible = false, annulled = false, visible = true)
            }

            QuestionState.CLOSED -> {
                this.copy(open = false, resolutionVisible = false, annulled = false, visible = true)
            }

            QuestionState.RESOLVED -> {
                this.copy(open = false, resolutionVisible = true, annulled = false, visible = true)
            }

            QuestionState.CANCELLED -> {
                this.copy(open = false, resolutionVisible = false, annulled = true, visible = true)
            }
        }
    }

    val state: QuestionState
        get() =
            if (!visible)
                QuestionState.DRAFT
            else if (annulled)
                QuestionState.CANCELLED
            else if (resolved && resolutionVisible)
                QuestionState.RESOLVED
            else if (!open)
                QuestionState.CLOSED
            else
                QuestionState.OPEN

    @Suppress("DEPRECATION")
    val groupPredictionVisibility: GroupPredictionVisibility
        get() = GroupPredictionVisibility.fromBooleans(groupPredVisible, groupPredRequirePrediction)

    override val urlPrefix get() = urlPrefix(id)

    /** The question's attribution policy with the room default filled in. Pure —
     *  the caller supplies the room, exactly as [effectiveScheduleIn]. Disclosure
     *  gates (e.g. [AttributionPolicy.aggregatesVisible]) live on the policy. */
    fun effectiveAttributionIn(room: Room?): AttributionPolicy =
        attribution ?: room?.defaultAttribution ?: AttributionPolicy()

    /** The question's schedule with the room default filled in. Pure — the caller
     *  supplies the room (client: RoomContext; server: the `Question.room` extension
     *  via the questionRoom fold). */
    fun effectiveScheduleIn(room: Room?) = schedule ?: room?.defaultSchedule ?: QuestionSchedule()
    companion object {
        fun urlPrefix(id: String) = "/questions/$id"
    }
}
