package tools.confido.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import tools.confido.extensions.Extension
import tools.confido.question.GroupPredictionVisibility
import tools.confido.question.QuestionState
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.rooms.Room

/** Where to navigate presentation followers as a side effect of an action. */
@Serializable
enum class ViewerNavigateTarget {
    /** No navigation. */
    NONE,
    /** Navigate to the associated question. */
    TO_QUESTION,
    /** Navigate to the first matching question (for tag-based actions). */
    TO_FIRST_QUESTION,
    /** Navigate back to the room page. */
    TO_ROOM,
}

/**
 * Base class for actions that can be executed as part of a presentation flow.
 *
 * Uses polymorphic serialization with an open class (not sealed) to allow
 * extensions to define custom action types.
 */
@Serializable
abstract class FlowAction {
    /** Human-readable description of what this action does. */
    abstract fun describe(): String
    /** If non-null, the presenter view should be set to this after execution. */
    open val presenterView: PresenterView? get() = null
    /** If non-null, presentation followers should be navigated to this URL.
     *  Called with room context when the action is processed during a presentation
     *  run (server-only; the [EntityLookup] is the server's resolver). */
    open fun getNavigateUrl(room: Room, lookup: EntityLookup): String? = null
    /** All question refs this action targets or displays, recursively. Used to
     *  validate that an authored flow stays within its room. Extensions must
     *  override for actions carrying question refs. */
    open fun questionRefs(): List<Ref<Question>> = emptyList()
    /** All presenter views this action can set, recursively. Used for validity
     *  checks when a flow is saved. */
    open fun embeddedViews(): List<PresenterView> = listOfNotNull(presenterView)
    /** Copy with every question ref (recursively, incl. views) transformed — the
     *  write-side twin of [questionRefs], used to substitute the loop sentinel at
     *  expansion. Extensions whose actions carry question refs must override. */
    open fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>): FlowAction = this
    /** Amend the [ExpectedWorld] with this action's authoring-time-predictable
     *  effects (question state changes, created marks); see [ExpectedWorld].
     *  Runtime-dependent effects have no representation here on purpose. */
    open fun simulate(world: ExpectedWorld): ExpectedWorld = world
    /** Human-readable reason this action is not yet fully configured (unset
     *  question, blank name, ...), or null when complete. A non-null value blocks
     *  saving in the flow editor and is rejected server-side. */
    open fun configProblem(): String? = null
}

/** Change a question's state (e.g. open, close, hide). */
@Serializable
@SerialName("setQuestionState")
data class SetQuestionStateAction(
    val question: Ref<Question>,
    val newState: QuestionState,
    /** Where to navigate presentation followers. */
    val navigateViewers: ViewerNavigateTarget = ViewerNavigateTarget.NONE,
    /** If non-null, also change the question's group prediction visibility. */
    val setGroupPredVisibility: GroupPredictionVisibility? = null,
) : FlowAction() {
    // Generic, deliberately deref-free (describe() serves string-only contexts and
    // fallbacks): the name-bearing rich description is a client UI concern —
    // components/redesign/presenter/Descriptions.kt renders it via useQuestion.
    override fun describe(): String {
        val base = "${newState.name.lowercase().replaceFirstChar { it.uppercase() }} question"
        return base + describeGroupPredVisibility(setGroupPredVisibility)
    }
    override fun getNavigateUrl(room: Room, lookup: EntityLookup): String? = when (navigateViewers) {
        ViewerNavigateTarget.TO_QUESTION -> room.urlPrefix + Question.urlPrefix(question.id)
        ViewerNavigateTarget.TO_ROOM -> room.urlPrefix
        else -> null
    }
    override fun questionRefs() = listOf(question)
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) = copy(question = f(question))
    override fun configProblem() = if (question.id.isEmpty()) "No question selected" else null
    override fun simulate(world: ExpectedWorld): ExpectedWorld {
        val q = world.question(question) ?: return world
        return world.withQuestion((setGroupPredVisibility?.apply(q) ?: q).withState(newState))
    }
}

/** Set a specific presenter view (e.g. show group predictions, invite link). */
@Serializable
@SerialName("setPresenterView")
data class SetPresenterViewAction(
    val view: PresenterView,
) : FlowAction() {
    override fun describe() = "Show ${view.describe()} on presenter"
    override val presenterView get() = view
    override fun questionRefs() = view.questionRefs()
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) = copy(view = view.mapQuestionRefs(f))
    override fun configProblem() = view.configProblem()
}

/** Change state for all questions matching a given tag. */
@Serializable
@SerialName("setTaggedQuestionsState")
data class SetTaggedQuestionsStateAction(
    val tag: String,
    val newState: QuestionState,
    /** Where to navigate presentation followers. */
    val navigateViewers: ViewerNavigateTarget = ViewerNavigateTarget.NONE,
    /** If true, all questions in the room that do NOT match the tag will be set to DRAFT (hidden). */
    val hideOtherQuestions: Boolean = false,
    /** If non-null, also change matching questions' group prediction visibility. */
    val setGroupPredVisibility: GroupPredictionVisibility? = null,
) : FlowAction() {
    override fun describe(): String {
        val base = "Set all \"$tag\" questions to ${newState.name.lowercase()}"
        val withHide = if (hideOtherQuestions) "$base (hide others)" else base
        return withHide + describeGroupPredVisibility(setGroupPredVisibility)
    }
    override fun getNavigateUrl(room: Room, lookup: EntityLookup): String? = when (navigateViewers) {
        ViewerNavigateTarget.TO_FIRST_QUESTION -> {
            val q = room.questions.mapNotNull { lookup.question(it) }.firstOrNull { tag in it.tags }
            if (q != null) room.urlPrefix + Question.urlPrefix(q.id) else null
        }
        ViewerNavigateTarget.TO_ROOM -> room.urlPrefix
        else -> null
    }
    override fun configProblem() = if (tag.isBlank()) "No tag specified" else null
    override fun simulate(world: ExpectedWorld): ExpectedWorld =
        world.room.questions.mapNotNull { world.question(it) }.fold(world) { w, q ->
            when {
                tag in q.tags -> w.withQuestion((setGroupPredVisibility?.apply(q) ?: q).withState(newState))
                hideOtherQuestions -> w.withQuestion(q.withState(QuestionState.DRAFT))
                else -> w
            }
        }
}

/** Navigate presentation followers to a specific question. */
@Serializable
@SerialName("navigateToQuestion")
data class NavigateToQuestionAction(
    val question: Ref<Question>,
) : FlowAction() {
    // Generic, deref-free — see SetQuestionStateAction.describe.
    override fun describe() = "Navigate viewers to a question"
    override fun getNavigateUrl(room: Room, lookup: EntityLookup) = room.urlPrefix + Question.urlPrefix(question.id)
    override fun questionRefs() = listOf(question)
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) = copy(question = f(question))
    override fun configProblem() = if (question.id.isEmpty()) "No question selected" else null
}

/** Navigate presentation followers to an arbitrary URL. */
@Serializable
@SerialName("autoNavigate")
data class NavigateViewersAction(
    val url: String,
) : FlowAction() {
    override fun describe() = "Navigate viewers to $url"
    override fun getNavigateUrl(room: Room, lookup: EntityLookup) = url
    override fun configProblem() = if (url.isBlank()) "No URL specified" else null
}

/** Execute multiple actions in sequence. */
@Serializable
@SerialName("composite")
data class CompositeAction(
    val actions: List<FlowAction>,
) : FlowAction() {
    override fun describe() = "${actions.size} actions"
    /** The last nested view wins, matching sequential-execution semantics. */
    override val presenterView: PresenterView?
        get() = actions.asReversed().firstNotNullOfOrNull { it.presenterView }
    override fun questionRefs() = actions.flatMap { it.questionRefs() }
    override fun embeddedViews() = actions.flatMap { it.embeddedViews() }
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) =
        copy(actions = actions.map { it.mapQuestionRefs(f) })
    override fun simulate(world: ExpectedWorld) = actions.fold(world) { w, a -> a.simulate(w) }
    override fun configProblem() = actions.firstNotNullOfOrNull { it.configProblem() }
}

/**
 * Create a timeline marker named [name] at the moment of execution — the
 * in-meeting "mark this moment" tool (question-scoped, or room-wide when
 * [question] is null). Overwrites a same-named marker, so a re-run flow moves
 * the mark instead of piling up duplicates; [MarkTP]-based compare slides
 * reference it by that name.
 */
@Serializable
@SerialName("createMark")
data class CreateMarkAction(
    val name: String = "",
    val question: Ref<Question>? = null,
) : FlowAction() {
    override fun describe() =
        if (question != null) "Create timeline marker \"$name\""
        else "Create room-wide timeline marker \"$name\""
    override fun questionRefs() = listOfNotNull(question)
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) = copy(question = question?.let(f))
    override fun configProblem() = if (name.isBlank()) "No marker name" else null
    override fun simulate(world: ExpectedWorld) =
        if (name.isBlank()) world else world.withMark(name, question)
}

/** Describe the group prediction visibility suffix for action descriptions. */
internal fun describeGroupPredVisibility(vis: GroupPredictionVisibility?): String = when (vis) {
    null -> ""
    GroupPredictionVisibility.EVERYONE -> " (show group pred to everyone)"
    GroupPredictionVisibility.ANSWERED -> " (show group pred to answered)"
    GroupPredictionVisibility.MODERATOR_ONLY -> " (hide group pred)"
}

val flowActionSM by lazy { SerializersModule {
    polymorphic(FlowAction::class) {
        subclass(SetQuestionStateAction::class)
        subclass(SetTaggedQuestionsStateAction::class)
        subclass(NavigateToQuestionAction::class)
        subclass(NavigateViewersAction::class)
        subclass(SetPresenterViewAction::class)
        subclass(CompositeAction::class)
        subclass(CreateMarkAction::class)
        Extension.forEach { it.registerFlowActions(this) }
    }
} }
