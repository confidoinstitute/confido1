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
import tools.confido.refs.deref
import tools.confido.utils.ellipsize
import rooms.Room

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
     *  Called with room context when the action is processed during a presentation run. */
    open fun getNavigateUrl(room: Room): String? = null
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
    override fun describe(): String {
        val qName = question.deref()?.name?.ellipsize(50) ?: "?"
        val base = "${newState.name.lowercase().replaceFirstChar { it.uppercase() }} \"$qName\""
        return base + describeGroupPredVisibility(setGroupPredVisibility)
    }
    override fun getNavigateUrl(room: Room): String? = when (navigateViewers) {
        ViewerNavigateTarget.TO_QUESTION -> room.urlPrefix + Question.urlPrefix(question.id)
        ViewerNavigateTarget.TO_ROOM -> room.urlPrefix
        else -> null
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
    override fun getNavigateUrl(room: Room): String? = when (navigateViewers) {
        ViewerNavigateTarget.TO_FIRST_QUESTION -> {
            val q = room.questions.mapNotNull { it.deref() }.firstOrNull { tag in it.tags }
            if (q != null) room.urlPrefix + Question.urlPrefix(q.id) else null
        }
        ViewerNavigateTarget.TO_ROOM -> room.urlPrefix
        else -> null
    }
}

/** Navigate presentation followers to a specific question. */
@Serializable
@SerialName("navigateToQuestion")
data class NavigateToQuestionAction(
    val question: Ref<Question>,
) : FlowAction() {
    override fun describe(): String {
        val qName = question.deref()?.name?.ellipsize(50) ?: "?"
        return "Navigate viewers to \"$qName\""
    }
    override fun getNavigateUrl(room: Room) = room.urlPrefix + Question.urlPrefix(question.id)
}

/** Navigate presentation followers to an arbitrary URL. */
@Serializable
@SerialName("autoNavigate")
data class NavigateViewersAction(
    val url: String,
) : FlowAction() {
    override fun describe() = "Navigate viewers to $url"
    override fun getNavigateUrl(room: Room) = url
}

/** Execute multiple actions in sequence. */
@Serializable
@SerialName("composite")
data class CompositeAction(
    val actions: List<FlowAction>,
) : FlowAction() {
    override fun describe() = "${actions.size} actions"
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
        Extension.forEach { it.registerFlowActions(this) }
    }
} }
