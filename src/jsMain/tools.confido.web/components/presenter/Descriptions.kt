package tools.confido.web.components.presenter

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
    if (ref == CURRENT_QUESTION) "each question in turn" else question.quotedName()

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
        is InviteLinkPV -> "the QR code to join"
        else -> view.describe()
    }
}

external interface FlowActionDescriptionProps : Props {
    var action: FlowAction
}

val FlowActionDescription = FC<FlowActionDescriptionProps> { props ->
    val action = props.action
    // Audience steps name their question when exactly one is picked.
    fun QuestionSelection.single() = questions.singleOrNull()?.takeIf { !all && tags.isEmpty() }
    val ref = when (action) {
        is SetQuestionStateAction -> action.question
        is NavigateToQuestionAction -> action.question
        is AskAudienceAction -> action.selection.single()
        is RevealAnswersAction -> action.selection.single()
        else -> null
    }
    val question = useQuestion(ref?.takeIf { it != CURRENT_QUESTION })
    when (action) {
        is SetQuestionStateAction -> {
            val base = "${action.newState.name.lowercase().replaceFirstChar { it.uppercase() }} ${describeQ(ref, question)}"
            +(base + describeGroupPredVisibility(action.setGroupPredVisibility))
        }
        is NavigateToQuestionAction -> +"Navigate viewers to ${describeQ(ref, question)}"
        is AskAudienceAction -> {
            val several = ref == null && action.selection.questions != listOf(CURRENT_QUESTION)
            val how = when {
                several && action.oneByOne && action.reveal == RevealTiming.RIGHT_AFTER -> " · one by one, results after each"
                several && action.oneByOne && action.reveal == RevealTiming.AT_END -> " · one by one, results at the end"
                several && action.oneByOne -> " · one by one"
                several && action.reveal != RevealTiming.LATER -> " · all at once, then results"
                several -> " · all at once"
                action.reveal != RevealTiming.LATER -> " · then results"
                else -> ""
            }
            +"Ask: ${if (ref != null) describeQ(ref, question) else action.selection.describe()}$how"
        }
        is RevealAnswersAction -> +("Reveal answers: ${if (ref != null) describeQ(ref, question) else action.selection.describe()}" +
            if (action.showResolution) " · with the correct answer" else "")
        is SetPresenterViewAction -> {
            +"Show "
            PresenterViewDescription { this.view = action.view }
            +" on the projector"
        }
        else -> +action.describe()
    }
}
