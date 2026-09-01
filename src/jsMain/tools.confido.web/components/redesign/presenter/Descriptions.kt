package tools.confido.web.components.redesign.presenter

import tools.confido.web.components.useQuestion
import react.FC
import react.Props
import tools.confido.question.Question
import tools.confido.refs.Ref
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

/** The loop sentinel gets a stable phrase instead of a (never-resolving) name lookup. */
private fun describeQ(ref: Ref<Question>?, question: Question?) =
    if (ref == CURRENT_QUESTION) "the current question" else question.quotedName()

val PresenterViewDescription = FC<PresenterViewDescriptionProps> { props ->
    val view = props.view
    val ref = when (view) {
        is QuestionPV -> view.question
        is GroupPredPV -> view.question
        is BinaryHistogramPV -> view.question
        is TimelinePV -> view.question
        is ComparePV -> view.question
        else -> null
    }
    // Deliberately unconditional: useQuestion must keep a stable hook order for its
    // future subscription-based implementation.
    val question = useQuestion(ref?.takeIf { it != CURRENT_QUESTION })
    val qName = describeQ(ref, question)
    +when (view) {
        is QuestionPV -> "question $qName"
        is GroupPredPV -> "group predictions for $qName"
        is BinaryHistogramPV -> "prediction histogram for $qName"
        is TimelinePV -> "prediction timeline for $qName"
        is ComparePV -> "comparison of ${view.tp1.describe()} vs ${view.tp2.describe()} for $qName"
        else -> view.describe()
    }
}

external interface FlowActionDescriptionProps : Props {
    var action: FlowAction
}

val FlowActionDescription = FC<FlowActionDescriptionProps> { props ->
    val action = props.action
    val ref = when (action) {
        is SetQuestionStateAction -> action.question
        is NavigateToQuestionAction -> action.question
        else -> null
    }
    val question = useQuestion(ref?.takeIf { it != CURRENT_QUESTION })
    when (action) {
        is SetQuestionStateAction -> {
            val base = "${action.newState.name.lowercase().replaceFirstChar { it.uppercase() }} ${describeQ(ref, question)}"
            +(base + describeGroupPredVisibility(action.setGroupPredVisibility))
        }
        is NavigateToQuestionAction -> +"Navigate viewers to ${describeQ(ref, question)}"
        is SetPresenterViewAction -> {
            +"Show "
            PresenterViewDescription { this.view = action.view }
            +" on presenter"
        }
        else -> +action.describe()
    }
}
