package tools.confido.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.serialization.RangeSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import tools.confido.payloads.requests.CalibrationRequest
import tools.confido.rooms.Room
import tools.confido.extensions.Extension
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.spaces.BinarySpace
import tools.confido.utils.unixNow

/**
 * Server-supplied entity resolver for the presentation-flow members that need
 * lookups ([PresenterView.isValid], [FlowAction.getNavigateUrl]). commonMain stays
 * deref-free — only the server calls these, passing its resolver; the overrides
 * stay colocated with their classes as pure functions of the lookup (this
 * replaces the originally planned jvm dispatch-`when` + extension hook, which
 * would have torn the per-class logic out of the class).
 */
interface EntityLookup {
    fun question(ref: Ref<Question>): Question?
    fun room(ref: Ref<Room>): Room?
}

@Serializable
abstract class PresenterView {
    open fun isValid(lookup: EntityLookup) = true
    abstract fun describe(): String
    /** All question refs this view displays. Used to validate that an authored
     *  flow stays within its room. */
    open fun questionRefs(): List<Ref<Question>> = emptyList()
    /** Copy with every question ref transformed (write-side twin of [questionRefs];
     *  substitutes the loop sentinel at expansion). Override alongside it. */
    open fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>): PresenterView = this
    /** Human-readable reason this view is not yet fully configured, or null when
     *  complete (see [FlowAction.configProblem]). */
    open fun configProblem(): String? = null
}


@Serializable
@SerialName("empty")
object EmptyPV: PresenterView() {
    override fun describe() = "blank screen"
}

@Serializable
@SerialName("question")
data class QuestionPV(
    val question: Ref<Question>
) : PresenterView() {
    override fun isValid(lookup: EntityLookup) = lookup.question(question) != null
    override fun describe() = "question text"
    override fun questionRefs() = listOf(question)
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) = copy(question = f(question))
    override fun configProblem() = if (question.id.isEmpty()) "No question selected" else null
}

@Serializable
@SerialName("groupPred")
data class GroupPredPV(
    val question: Ref<Question>,
    val showResolution: Boolean = false,
    /** Numeric value range to show zoomed to (as on the presenting graph); null = full range. */
    @Serializable(with = RangeSerializer::class)
    val view: ClosedRange<Double>? = null,
) : PresenterView() {
    override fun isValid(lookup: EntityLookup) = lookup.question(question) != null
    override fun describe() = "group predictions for this question"
    override fun questionRefs() = listOf(question)
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) = copy(question = f(question))
    override fun configProblem() = if (question.id.isEmpty()) "No question selected" else null
}

@Serializable
@SerialName("binaryHistogram")
data class BinaryHistogramPV(
    val question: Ref<Question>,
    /** Show nicknames of predictors in the histogram bars (available only when the
     *  presenting user may view individual predictions). */
    val showNames: Boolean = false,
) : PresenterView() {
    override fun isValid(lookup: EntityLookup) = lookup.question(question)?.answerSpace is BinarySpace
    override fun describe() = "prediction histogram"
    override fun questionRefs() = listOf(question)
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) = copy(question = f(question))
    override fun configProblem() = if (question.id.isEmpty()) "No question selected" else null
}

@Serializable
@SerialName("calibReq")
data class CalibrationReqPV(val req: CalibrationRequest) : PresenterView() {
    override fun describe() = "Calibration graph"
}

@Serializable
@SerialName("inviteLink")
data class InviteLinkPV(
    val room: Ref<Room>,
    val id: String,
    /** If true, users joining via this invite link will automatically follow the presentation. */
    val followPresentation: Boolean = false,
) : PresenterView() {
    override fun isValid(lookup: EntityLookup) = this.id == "FIRST" || lookup.room(room)?.inviteLinks?.any { it.id == this.id } ?: false
    override fun describe() = "invite link and QR code"
    // The room ref is backfilled automatically by the flow editor; this only
    // guards against hand-crafted payloads.
    override fun configProblem() = if (room.id.isEmpty()) "No room set" else null
}

/** Displays a slide image from a presentation. */
@Serializable
@SerialName("slide")
data class SlidePV(
    val presentationId: String,
    val slideIndex: Int,
    /** File access token for the slide image URL. */
    val fileToken: String,
    /** Mirrors [Presentation.slidesVersion] — cache-busts the image URL. */
    val slidesVersion: Int = 0,
) : PresenterView() {
    override fun describe() = "presentation slide"
    fun slideUrl() = slideArtifactUrl(presentationId, fileToken, slidesVersion, slideIndex, "png")
    /** Vector rendering for the projector (crisp at any resolution); PNG if the server has none. */
    fun svgUrl() = slideArtifactUrl(presentationId, fileToken, slidesVersion, slideIndex, "svg")
}

/**
 * A proxy presenter view that delegates to the room's current presentation
 * run state. The presenter window subscribes to the room's presentation
 * WebSocket and renders whatever the current effective view is.
 */
@Serializable
@SerialName("presentation")
data class PresentationPV(
    val room: Ref<Room>,
) : PresenterView() {
    override fun describe() = "live presentation"
}

/**
 * A reference to a moment in time, resolved when the view renders. [MarkTP] is
 * name-based on purpose: presentation flows create marks mid-run (with generated
 * ids), so a pre-authored "compare vs ‹Discussion start›" slide can only refer
 * to the mark it will create by name.
 */
@Serializable
sealed class TimepointRef {
    abstract fun describe(): String
}

/** The current state (live). */
@Serializable
@SerialName("now")
data object NowTP : TimepointRef() {
    override fun describe() = "now"
}

/** A fixed unix timestamp. */
@Serializable
@SerialName("at")
data class AtTP(val ts: Int) : TimepointRef() {
    override fun describe() = "a fixed time"
}

/** A timeline marker looked up by name — question-scoped when [question] is set,
 *  else on the room's marks. */
@Serializable
@SerialName("mark")
data class MarkTP(val name: String, val question: Ref<Question>? = null) : TimepointRef() {
    override fun describe() = "marker \"$name\""
}

private fun TimepointRef.mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>): TimepointRef =
    if (this is MarkTP && question != null) copy(question = f(question)) else this

/** The group prediction history timeline, optionally windowed to a fixed range. */
@Serializable
@SerialName("timeline")
data class TimelinePV(
    val question: Ref<Question>,
    val windowStart: Int? = null,
    val windowEnd: Int? = null,
) : PresenterView() {
    override fun isValid(lookup: EntityLookup) = lookup.question(question) != null
    override fun describe() = "prediction timeline"
    override fun questionRefs() = listOf(question)
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) = copy(question = f(question))
    override fun configProblem() = if (question.id.isEmpty()) "No question selected" else null
}

enum class CompareViewMode { DISTRIBUTIONS, CHANGES, SCATTER }

/** Two moments of a question's predictions side by side: the group distributions
 *  diff, or per-predictor movements (changes slope / scatter). */
@Serializable
@SerialName("compare")
data class ComparePV(
    val question: Ref<Question>,
    val tp1: TimepointRef,
    val tp2: TimepointRef = NowTP,
    val mode: CompareViewMode = CompareViewMode.DISTRIBUTIONS,
    /** Label individual movements with predictor names (only meaningful for the
     *  individual modes, and only when the viewing session may see them). */
    val showNames: Boolean = false,
    /** Numeric value range the distributions plot shows zoomed to; null = full range. */
    @Serializable(with = RangeSerializer::class)
    val view: ClosedRange<Double>? = null,
) : PresenterView() {
    override fun isValid(lookup: EntityLookup) = lookup.question(question) != null
    override fun describe() = "comparison of two moments"
    override fun questionRefs() = listOfNotNull(question,
        (tp1 as? MarkTP)?.question, (tp2 as? MarkTP)?.question)
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) =
        copy(question = f(question), tp1 = tp1.mapQuestionRefs(f), tp2 = tp2.mapQuestionRefs(f))
    override fun configProblem() = when {
        question.id.isEmpty() -> "No question selected"
        (tp1 as? MarkTP)?.name?.isBlank() == true || (tp2 as? MarkTP)?.name?.isBlank() == true ->
            "No marker selected"
        else -> null
    }
}

/** Displays multiple questions side by side in an adaptive grid. */
@Serializable
@SerialName("multiQuestion")
data class MultiQuestionPV(
    val questions: List<Ref<Question>>,
    val showGroupPred: Boolean = true,
    val showHistogram: Boolean = false,
    val showResolution: Boolean = false,
) : PresenterView() {
    override fun isValid(lookup: EntityLookup) = questions.all { lookup.question(it) != null }
    override fun describe() = "${questions.size}-question overview"
    override fun questionRefs() = questions
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) = copy(questions = questions.map(f))
    override fun configProblem() =
        if (questions.isEmpty() || questions.any { it.id.isEmpty() }) "No questions selected" else null
}


const val PRESENTER_LIFETIME = 30 * 60

@Serializable
data class PresenterInfo(
    //val token : String, // TODO: (later) think about link-sharable presenter views (e.g. in order to show it on
                          // another device
    //val user: Ref<User>,
    val view: PresenterView = EmptyPV,
    val lastUpdate: Int = unixNow(),
) {
    val isValid get() = unixNow() - lastUpdate <= PRESENTER_LIFETIME
}

val presenterSM by lazy { SerializersModule {
    polymorphic(PresenterView::class) {
        subclass(EmptyPV::class)
        subclass(QuestionPV::class)
        subclass(GroupPredPV::class)
        subclass(BinaryHistogramPV::class)
        subclass(InviteLinkPV::class)
        subclass(CalibrationReqPV::class)
        subclass(SlidePV::class)
        subclass(PresentationPV::class)
        subclass(MultiQuestionPV::class)
        subclass(TimelinePV::class)
        subclass(ComparePV::class)
        Extension.forEach { it.registerPresenterViews(this) }
    }
} }