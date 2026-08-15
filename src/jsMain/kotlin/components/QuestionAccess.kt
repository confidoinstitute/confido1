package components

import hooks.useSubscription
import payloads.responses.WSData
import react.use
import tools.confido.question.Question
import tools.confido.refs.Ref
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
    // Transitional bridge: while the global questions slice is still root-subscribed
    // it covers the subscription's initial-load gap (and pre-existing consumers).
    // Dies with the slice at the eviction, leaving the subscription alone.
    return (sub as? WSData)?.data ?: ref?.let { use(AppStateContext)?.questions?.get(it.id) }
}
