package tools.confido.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
}

@Serializable
@SerialName("groupPred")
data class GroupPredPV(
    val question: Ref<Question>,
    val showResolution: Boolean = false,
) : PresenterView() {
    override fun isValid(lookup: EntityLookup) = lookup.question(question) != null
    override fun describe() = "group predictions for this question"
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
}

/** Displays a slide image from a presentation. */
@Serializable
@SerialName("slide")
data class SlidePV(
    val presentationId: String,
    val slideIndex: Int,
    /** File access token for the slide image URL. */
    val fileToken: String,
) : PresenterView() {
    override fun describe() = "presentation slide"
    fun slideUrl(): String {
        val num = (slideIndex + 1).toString().padStart(4, '0')
        return "/presentations/$presentationId/$fileToken/slides/$num.png"
    }
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
        Extension.forEach { it.registerPresenterViews(this) }
    }
} }