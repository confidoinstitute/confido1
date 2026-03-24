package rooms

import kotlinx.serialization.Serializable
import tools.confido.question.Question
import tools.confido.refs.Ref

/**
 * Represents a question in an effective question list (e.g. aggregated from child rooms).
 * Each entry carries flags controlling how the question is used.
 */
@Serializable
data class EffectiveQuestion(
    /** Reference to the question. */
    val question: Ref<Question>,
    /** The room this question originally belongs to. */
    val sourceRoom: Ref<Room>,
    /** Whether this question should appear in the room's question list UI. */
    val visibleInList: Boolean = true,
    /** Whether this question should be included in scoreboard calculations. */
    val includedInScoring: Boolean = true,
)
