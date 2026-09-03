package tools.confido.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.question.Question
import tools.confido.rooms.Room
import tools.confido.refs.Entity
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.refs.ServerImmediateDerefEntity
import tools.confido.utils.HasUrlPrefix

/** Largest slide PDF accepted (upload or URL import). Shared so the client can refuse early. */
const val MAX_SLIDES_PDF_BYTES: Long = 50L * 1024 * 1024
/** Most slides per presentation — a proxy for rendered-PNG storage (~0.3 MB per slide at 150 DPI). */
const val MAX_SLIDES = 200

/**
 * A presentation flow associated with a room. Contains an ordered list of
 * flow items (slides, actions, presenter views, notes) that can be stepped
 * through during a live session.
 */
@Serializable
data class Presentation(
    @SerialName("_id") override val id: String = "",
    val room: Ref<Room>,
    val name: String = "Presentation",
    val items: List<FlowItem> = emptyList(),
    /** Total number of slide images from uploaded PDF. */
    val numSlides: Int = 0,
    /** User-editable labels for each slide, indexed by slide number. Defaults to "Slide 1", etc. */
    val slideLabels: List<String> = emptyList(),
    /** Bumped on every slide (re-)upload; cache-busts slide image URLs, since the
     *  file names stay the same across a replace. */
    val slidesVersion: Int = 0,
    /**
     * Random token required in slide image URLs as basic access protection.
     *
     * Unlike [id], this is a secret and must come from a cryptographically secure
     * generator, so it is always supplied by the server rather than defaulted here.
     * The empty default exists only so that stored documents keep deserializing;
     * an empty token never authorizes access.
     */
    val fileToken: String = "",
) : ServerImmediateDerefEntity, HasUrlPrefix {
    override val urlPrefix get() = "/presentations/$id"

    /** Build the URL path for a slide image. */
    fun slideUrl(slideIndex: Int): String {
        val num = (slideIndex + 1).toString().padStart(4, '0')
        return "/presentations/$id/$fileToken/slides/$num.png?v=$slidesVersion"
    }
}

/**
 * A single item in a presentation flow. Items form a flat ordered list
 * that is stepped through sequentially during a presentation run.
 */
@Serializable
sealed class FlowItem {
    /** Unique ID within the presentation, used for DnD ordering and action tracking. */
    abstract val id: String
}

/** A slide from the uploaded PDF. */
@Serializable
@SerialName("slide")
data class SlideItem(
    override val id: String = "",
    /** Zero-based index into the presentation's slide images. */
    val slideIndex: Int,
) : FlowItem()

/** Execute one or more server-side actions (e.g. change question state). */
@Serializable
@SerialName("action")
data class ActionItem(
    override val id: String = "",
    val action: FlowAction,
) : FlowItem()

/** A presenter note, shown only on the controller (not on the projector). */
@Serializable
@SerialName("note")
data class NoteItem(
    override val id: String = "",
    val text: String,
    /** If true, shown prominently as a warning/reminder. */
    val warning: Boolean = false,
) : FlowItem()

/**
 * Repeat [body] for every matching room question. Expanded at presentation start
 * into one substituted body copy per question (in room question order), so the
 * running presentation only ever sees ordinary flat items — and questions tagged
 * after authoring are picked up.
 *
 * Body steps reference the iteration's question via the [CURRENT_QUESTION]
 * sentinel. Bodies may not contain slides or nested loops (there is exactly one
 * implicit loop variable, by design — permanently).
 */
@Serializable
@SerialName("forEach")
data class ForEachItem(
    override val id: String = "",
    /** Dynamic check: every room question (incl. ones added later). */
    val all: Boolean = false,
    /** Dynamic checks: questions carrying any of these tags (incl. later-added/tagged). */
    val tags: Set<String> = emptySet(),
    /** Static picks: exactly these questions (later-added ones are NOT included). */
    val questions: List<Ref<Question>> = emptyList(),
    val body: List<FlowItem> = emptyList(),
) : FlowItem() {
    /** Whether [q] is iterated over — the union of all checks. */
    fun selects(q: Question): Boolean = all || q.tags.any { it in tags } || q.ref in questions

    fun toggleAll() = copy(all = !all)
    fun toggleTag(t: String) = copy(tags = if (t in tags) tags - t else tags + t)

    /**
     * Toggle one question. Selecting adds a static pick. Deselecting a question
     * covered only by dynamic checks first *dissolves* the covering checks into
     * static picks (minus the question) — targeted, so non-covering tags keep
     * their include-later-questions dynamism.
     */
    fun toggleQuestion(q: Question, roomQuestions: List<Question>): ForEachItem {
        if (!selects(q)) return copy(questions = questions + q.ref)
        var result = this
        for (t in tags.filter { it in q.tags }) {
            result = result.copy(tags = result.tags - t,
                questions = (result.questions +
                    roomQuestions.filter { t in it.tags && it.id != q.id }.map { it.ref }).distinct())
        }
        if (result.all) {
            result = result.copy(all = false,
                questions = (result.questions +
                    roomQuestions.filter { it.id != q.id }.map { it.ref }).distinct())
        }
        return result.copy(questions = result.questions.filter { it.id != q.id })
    }

    /** Selection phrasing without access to the question list (row labels etc.). */
    fun describeSelection(): String = when {
        all -> "all room questions"
        tags.isNotEmpty() && questions.isEmpty() ->
            "questions tagged ${tags.sorted().joinToString(", ") { "\"$it\"" }}"
        tags.isEmpty() && questions.isNotEmpty() ->
            "${questions.size} picked ${if (questions.size == 1) "question" else "questions"}"
        tags.isEmpty() && questions.isEmpty() -> "no questions (empty selection)"
        else -> "a custom selection"
    }
}

/**
 * Placeholder for a [ForEachItem] iteration's question. Only legal inside loop
 * bodies; replaced with the concrete question at expansion (and treated as
 * "unknown but will exist" by authoring-time editors, which gate against the
 * loop's candidate set instead — see ExpectedWorld.loopCandidates).
 */
val CURRENT_QUESTION: Ref<Question> = Ref("~current~")

/** Short human-readable description of a flow item, for lists and hints. */
fun FlowItem.shortDescribe(pres: Presentation): String = when (this) {
    is SlideItem -> pres.slideLabels.getOrElse(slideIndex) { "Slide ${slideIndex + 1}" }
    is ActionItem -> action.describe()
    is NoteItem -> (if (warning) "⚠ " else "") + text.ifEmpty { "(empty note)" }
    is ForEachItem -> "Repeat over ${describeSelection()}" +
        " (${body.size} ${if (body.size == 1) "step" else "steps"})"
}

/** All question refs a flow item targets or displays (see [FlowAction.questionRefs]).
 *  The loop sentinel is excluded — it is not a real question. */
fun FlowItem.questionRefs(): List<Ref<Question>> = when (this) {
    is ActionItem -> action.questionRefs()
    is ForEachItem -> (questions + body.flatMap { it.questionRefs() })
        .filter { it != CURRENT_QUESTION }
    else -> emptyList()
}

/** Replace the [CURRENT_QUESTION] sentinel with a concrete question (loop expansion). */
fun FlowItem.substituteQuestion(q: Ref<Question>): FlowItem = when (this) {
    is ActionItem -> copy(action = action.mapQuestionRefs { if (it == CURRENT_QUESTION) q else it })
    else -> this
}

/** Copy with a different id ([FlowItem] has no `copy` on the base class). */
fun FlowItem.withId(newId: String): FlowItem = when (this) {
    is SlideItem -> copy(id = newId)
    is ActionItem -> copy(id = newId)
    is NoteItem -> copy(id = newId)
    is ForEachItem -> copy(id = newId)
}

/**
 * Enforce the slide-order invariant: slide items always appear in ascending
 * [SlideItem.slideIndex] order with no duplicates. Slide items keep their *slot
 * positions* in the list — only the slots' contents are re-sorted (surplus slots
 * left over from dropped duplicates are removed), so non-slide items never move.
 * Auto-populating missing slides is just `normalizeFlowItems(items + missing)`:
 * the new slides sink into their proper slots.
 */
fun normalizeFlowItems(items: List<FlowItem>): List<FlowItem> {
    val slides = items.filterIsInstance<SlideItem>().distinctBy { it.slideIndex }.sortedBy { it.slideIndex }
    var next = 0
    return items.mapNotNull { item ->
        if (item is SlideItem) slides.getOrNull(next++) else item
    }
}

/**
 * Server-side run state for a presentation. Maintained in memory (not persisted),
 * scoped to the room. Sent to moderator clients via the presentation WebSocket.
 */
@Serializable
data class PresentationRunState(
    val currentIndex: Int = 0,
    /** Flat list of items to step through. Built at presentation start by expanding any
     *  expandable actions (meta-items) in the original [Presentation.items]. */
    val expandedItems: List<FlowItem> = emptyList(),
    /** The effective presenter view to display (derived from the current item). */
    val currentView: PresenterView? = null,
    /** URL that presentation followers should currently be navigated to. Updated when the
     *  engine lands on an action whose [FlowAction.getNavigateUrl] returns non-null. */
    val currentNavigateUrl: String? = null,
    /** IDs of action items that have been executed in this run (to avoid re-execution). */
    val executedActionIds: Set<String> = emptySet(),
    /** ID of the action that was executed on the most recent navigation step (null if none). */
    val lastExecutedId: String? = null,
    /** Live per-step view tweaks made through the presenter controls, keyed by flow
     *  item id. The effective view is `viewOverrides[item.id] ?: scripted`; overrides
     *  are sticky for the run (they survive navigating away and back) and die with it. */
    val viewOverrides: Map<String, PresenterView> = emptyMap(),
)

/**
 * Combined data sent over the presentation WebSocket endpoint.
 */
@Serializable
data class PresentationWSData(
    val presentation: Presentation,
    /** Null when the presentation is not running. */
    val runState: PresentationRunState? = null,
)
