package tools.confido.state

import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.rooms.Room

/**
 * A hypothetical world state as expected at a given step of a presentation flow:
 * live entities amended by simulating the preceding steps' authoring-time-predictable
 * effects ([FlowAction.simulate] — question state changes, created marks). Flow-step
 * editors render presenter controls and action editors against it, so pickers can
 * offer things that will only exist once the flow runs (e.g. marks created by
 * earlier steps) and reference lines can show effective-at-this-step state.
 *
 * Its mere *presence* (a non-null context on the client) also signals rendering
 * against a future world: gates on runtime-unknowable conditions (participant
 * predictions, a resolution entered later) should render permissively with a hint
 * rather than lock up. Immutable — simulation folds amended copies.
 */
data class ExpectedWorld(
    val room: Room,
    private val lookup: EntityLookup,
    /** Amended question copies, keyed by id; unamended questions fall through to [lookup]. */
    val questions: Map<String, Question> = emptyMap(),
    /** Marks that will have been created by preceding flow steps. */
    val marks: List<ExpectedMark> = emptyList(),
    /** Index of the flow item currently being simulated (recorded on created marks). */
    val stepIndex: Int = 0,
    /** Non-null while editing a [ForEachItem] body: the questions the loop iterates
     *  over (in their expected-at-this-step state). Editors gate three-valued
     *  against this set — all-agree gates normally, none-agree hides, mixed is
     *  permissive with a hint. */
    val loopCandidates: List<Question>? = null,
) {
    fun question(ref: Ref<Question>?): Question? = ref?.let { questions[it.id] ?: lookup.question(it) }
    /** The questions [loop] iterates over (expected copies, room order). */
    fun candidatesFor(loop: ForEachItem): List<Question> =
        room.questions.mapNotNull { question(it) }.filter { loop.selects(it) }
    fun withQuestion(q: Question) = copy(questions = questions + (q.id to q))
    /** Record a mark creation; a same-named mark is replaced, mirroring
     *  [CreateMarkAction]'s overwrite-by-name run-time semantics. */
    fun withMark(name: String, question: Ref<Question>?) =
        copy(marks = marks.filter { it.name != name } + ExpectedMark(name, question, stepIndex))
}

/** A mark expected to exist because the flow item at [stepIndex] creates it
 *  ([question] null = room-wide). */
data class ExpectedMark(val name: String, val question: Ref<Question>?, val stepIndex: Int)

/** Simulate the effects of the flow items before [beforeIndex] on the live world. */
fun computeExpectedWorld(items: List<FlowItem>, beforeIndex: Int, room: Room, lookup: EntityLookup): ExpectedWorld =
    items.take(beforeIndex.coerceAtLeast(0)).foldIndexed(ExpectedWorld(room, lookup)) { idx, world, item ->
        when (item) {
            is ActionItem -> item.action.simulate(world.copy(stepIndex = idx))
            is ForEachItem -> {
                // Mirror expansion: fold the substituted body once per candidate.
                val w0 = world.copy(stepIndex = idx)
                w0.candidatesFor(item).fold(w0) { w, q ->
                    item.body.fold(w) { wb, b ->
                        ((b.substituteQuestion(q.ref) as? ActionItem)?.action?.simulate(wb)) ?: wb
                    }
                }
            }
            else -> world
        }
    }
