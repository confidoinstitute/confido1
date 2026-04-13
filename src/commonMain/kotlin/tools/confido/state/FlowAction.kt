package tools.confido.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import tools.confido.extensions.Extension
import tools.confido.question.QuestionState
import tools.confido.question.Question
import tools.confido.refs.Ref

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
}

/** Change a question's state (e.g. open, close, hide). */
@Serializable
@SerialName("setQuestionState")
data class SetQuestionStateAction(
    val question: Ref<Question>,
    val newState: QuestionState,
) : FlowAction() {
    override fun describe() = "Set question state to ${newState.name.lowercase()}"
}

/** Set a specific presenter view (e.g. show group predictions, invite link). */
@Serializable
@SerialName("setPresenterView")
data class SetPresenterViewAction(
    val view: PresenterView,
) : FlowAction() {
    override fun describe() = "Show ${view.describe()} on presenter"
}

/** Change state for all questions matching a given tag. */
@Serializable
@SerialName("setTaggedQuestionsState")
data class SetTaggedQuestionsStateAction(
    val tag: String,
    val newState: QuestionState,
) : FlowAction() {
    override fun describe() = "Set all \"$tag\" questions to ${newState.name.lowercase()}"
}

/** Navigate all guest users to a URL via the auto_navigate extension. */
@Serializable
@SerialName("autoNavigate")
data class AutoNavigateAction(
    val url: String,
) : FlowAction() {
    override fun describe() = "Navigate users to $url"
}

/** Execute multiple actions in sequence. */
@Serializable
@SerialName("composite")
data class CompositeAction(
    val actions: List<FlowAction>,
) : FlowAction() {
    override fun describe() = "${actions.size} actions"
}

val flowActionSM by lazy { SerializersModule {
    polymorphic(FlowAction::class) {
        subclass(SetQuestionStateAction::class)
        subclass(SetTaggedQuestionsStateAction::class)
        subclass(AutoNavigateAction::class)
        subclass(SetPresenterViewAction::class)
        subclass(CompositeAction::class)
        Extension.forEach { it.registerFlowActions(this) }
    }
} }
