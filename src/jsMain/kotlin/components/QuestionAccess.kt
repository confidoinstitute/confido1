package components

import react.use
import tools.confido.question.Question
import tools.confido.refs.Ref

/**
 * Access a question by ref from client render code (client endgame; agreed
 * 2026-08-15). The strategy behind this accessor evolves without its callers
 * changing:
 *  - v0 (now): reads the root-subscribed global questions slice.
 *  - v1 (per-room questions split / eviction): subscribes the `question/<id>`
 *    topic. Overlap with collection topics is deliberate rather than optimized
 *    away: MultiplexWS dedups same-topic consumers, and the sender's flush pass
 *    recomputes all subscribed topics against one snapshot into one atomic frame,
 *    so duplicated data is *consistent* duplication — it costs bounded wire bytes,
 *    never a divergence window.
 *  - v2 (only if profiling ever demands): serve from a covering collection
 *    subscription when one is active ("subsumption"), implemented inside this
 *    accessor/cache layer, invisible to callers.
 *
 * Call discipline: although the v0 implementation is an unrestricted `use`, treat
 * this as a real hook — call it unconditionally with a stable order, passing null
 * instead of branching around it (hence the nullable ref). The v1 implementation
 * will be subscription-backed and needs that stability to be a drop-in swap.
 */
fun useQuestion(ref: Ref<Question>?): Question? =
    ref?.let { use(AppStateContext)?.questions?.get(it.id) }
