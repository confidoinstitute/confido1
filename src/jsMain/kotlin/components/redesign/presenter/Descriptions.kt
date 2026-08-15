package components.redesign.presenter

import components.useQuestion
import react.FC
import react.Props
import tools.confido.question.Question
import tools.confido.state.*
import tools.confido.utils.ellipsize

/**
 * Rich, live descriptions of presenter views and flow actions. Descriptions are a
 * UI concern, so they are generated client-side as components (agreed 2026-08-15):
 * a component rather than a string so it can read question data via [useQuestion] —
 * live today through the questions slice, and a per-question subscription after the
 * eviction, without any call site changing. Unknown and extension classes fall back
 * to the generic commonMain `describe()` (which stays deref-free); string-only
 * contexts (the offer toast) use `describe()` directly.
 */
external interface PresenterViewDescriptionProps : Props {
    var view: PresenterView
}

private fun Question?.quotedName() = "“${this?.name?.ellipsize(50) ?: "?"}”"

val PresenterViewDescription = FC<PresenterViewDescriptionProps> { props ->
    val view = props.view
    // Deliberately unconditional: useQuestion must keep a stable hook order for its
    // future subscription-based implementation.
    val question = useQuestion(
        when (view) {
            is QuestionPV -> view.question
            is GroupPredPV -> view.question
            is BinaryHistogramPV -> view.question
            else -> null
        }
    )
    +when (view) {
        is QuestionPV -> "question ${question.quotedName()}"
        is GroupPredPV -> "group predictions for ${question.quotedName()}"
        is BinaryHistogramPV -> "prediction histogram for ${question.quotedName()}"
        else -> view.describe()
    }
}

external interface FlowActionDescriptionProps : Props {
    var action: FlowAction
}

val FlowActionDescription = FC<FlowActionDescriptionProps> { props ->
    val action = props.action
    val question = useQuestion(
        when (action) {
            is SetQuestionStateAction -> action.question
            is NavigateToQuestionAction -> action.question
            else -> null
        }
    )
    when (action) {
        is SetQuestionStateAction -> {
            val base = "${action.newState.name.lowercase().replaceFirstChar { it.uppercase() }} ${question.quotedName()}"
            +(base + describeGroupPredVisibility(action.setGroupPredVisibility))
        }
        is NavigateToQuestionAction -> +"Navigate viewers to ${question.quotedName()}"
        is SetPresenterViewAction -> {
            +"Show "
            PresenterViewDescription { this.view = action.view }
            +" on presenter"
        }
        else -> +action.describe()
    }
}
