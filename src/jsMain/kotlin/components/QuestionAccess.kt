package components

import hooks.useSubscription
import payloads.responses.WSData
import react.FC
import react.Props
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.state.QuestionCounts
import tools.confido.state.Topics

/**
 * Access a question by ref from client render code (client endgame; agreed
 * 2026-08-15): a real hook subscribing the `question/<id>` topic (v1). Overlap
 * with collection topics is deliberate rather than optimized away: MultiplexWS
 * dedups same-topic consumers, and the sender's flush pass recomputes all
 * subscribed topics against one snapshot into one atomic frame, so duplicated
 * data is *consistent* duplication — it costs bounded wire bytes, never a
 * divergence window. A possible v2 (serve from a covering collection subscription
 * — "subsumption") would live inside this accessor, invisible to callers, and is
 * deferred unless profiling ever demands it.
 *
 * Call discipline: a real hook — call unconditionally with a stable order,
 * passing null instead of branching around it (hence the nullable ref).
 */
fun useQuestion(ref: Ref<Question>?): Question? {
    val sub = useSubscription(ref?.let { Topics.question(it) })
    return (sub as? WSData)?.data
}

/**
 * Live per-question counts (`question/<id>/counts`) — what used to be the
 * globalState-reading `Question.numPredictions`/`numPredictors` props. Same
 * call discipline as [useQuestion]; zeros while loading.
 */
fun useQuestionCounts(ref: Ref<Question>?): QuestionCounts {
    val sub = useSubscription(ref?.let { Topics.questionCounts(it) })
    return (sub as? WSData)?.data ?: QuestionCounts()
}

external interface QuestionPredictorsProps : Props {
    var question: Ref<Question>
}

/** The question's live predictor count as a plain text node — a component rather
 *  than a hook so render-position sites (tile footers, table cells inside loops)
 *  stay hook-safe. Composes with surrounding text: `QuestionPredictors {...}; +" people answered"`. */
val QuestionPredictors = FC<QuestionPredictorsProps> { props ->
    +"${useQuestionCounts(props.question).predictors}"
}
