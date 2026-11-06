package tools.confido.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.question.GroupPredictionVisibility
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.rooms.Room

/**
 * What presentation participants see *on their own devices* (the projector is
 * always the presenter's). Drives what [AskAudienceAction] / [RevealAnswersAction]
 * do and whether a run hides the room at start ([Presentation.startSnapshot]).
 * Advanced (primitive) steps ignore it — they always do exactly what they say.
 *
 * The names describe the audience's experience, not the mechanism, so the
 * implementation can later move from changing the questions themselves to a
 * presentation-scoped overlay without the setting changing meaning.
 */
@Serializable
enum class AudienceMode {
    /** Questions appear when asked, results when revealed. Hides the room at start. */
    ONE_AT_A_TIME,
    /** Devices follow the presenter; question states are left alone. */
    GUIDED,
    /** Only the projector follows the presenter; people browse freely. */
    FREE,
}

/** What happens to the room when a [AudienceMode.ONE_AT_A_TIME] run stops (at the end or midway). */
@Serializable
enum class PresentationEndBehavior {
    /** Leave things as the presentation left them: revealed questions stay visible
     *  with their results, unreached ones stay hidden. */
    KEEP_COVERED,
    /** Restore every question's state and group-answer visibility from [Presentation.startSnapshot]. */
    RESTORE,
}

/** When an [AskAudienceAction] reveals what people answered. */
@Serializable
enum class RevealTiming {
    /** On the next click: after each question when asking one by one. */
    RIGHT_AFTER,
    /** One by one: all questions first, then all results together. */
    AT_END,
    /** Not part of this step — a separate [RevealAnswersAction] does it. */
    LATER,
}

/** A question's audience-facing state as it was when a run started. */
@Serializable
data class QuestionSnapshot(
    val state: QuestionState,
    val groupPredVisibility: GroupPredictionVisibility,
) {
    fun applyTo(q: Question) = groupPredVisibility.apply(q.withState(state))

    companion object {
        fun of(q: Question) = QuestionSnapshot(q.state, q.groupPredictionVisibility)
    }
}

/**
 * A set of room questions, as picked in the editor: [all] and [tags] are dynamic
 * checks (they keep including questions added or tagged later), [questions] are
 * static picks. May contain [CURRENT_QUESTION] inside a loop body.
 */
@Serializable
data class QuestionSelection(
    val all: Boolean = false,
    val tags: Set<String> = emptySet(),
    val questions: List<Ref<Question>> = emptyList(),
) {
    val isEmpty get() = !all && tags.isEmpty() && questions.isEmpty()

    /** Whether [q] is selected — the union of all checks. */
    fun selects(q: Question): Boolean = all || q.tags.any { it in tags } || q.ref in questions

    /** The selected questions in room order. The loop sentinel is not resolved here. */
    fun resolve(roomQuestions: List<Question>) = roomQuestions.filter { selects(it) }

    fun toggleAll() = copy(all = !all)
    fun toggleTag(t: String) = copy(tags = if (t in tags) tags - t else tags + t)

    /**
     * Toggle one question. Selecting adds a static pick. Deselecting a question
     * covered only by dynamic checks first *dissolves* the covering checks into
     * static picks (minus the question) — targeted, so non-covering tags keep
     * their include-later-questions dynamism.
     */
    fun toggleQuestion(q: Question, roomQuestions: List<Question>): QuestionSelection {
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
    fun describe(): String = when {
        questions == listOf(CURRENT_QUESTION) && !all && tags.isEmpty() -> "each question in turn"
        all -> "all room questions"
        tags.isNotEmpty() && questions.isEmpty() ->
            "questions tagged ${tags.sorted().joinToString(", ") { "\"$it\"" }}"
        tags.isEmpty() && questions.size == 1 -> "1 picked question"
        tags.isEmpty() && questions.isNotEmpty() -> "${questions.size} picked questions"
        isEmpty -> "no questions (empty selection)"
        else -> "a custom selection"
    }

    fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) = copy(questions = questions.map(f))
}

/**
 * Common shape of the two audience steps. [selection] is what the author picked;
 * [resolved] and [mode] are filled in when a run starts (expansion), so a running
 * step knows its concrete questions and the presentation's [AudienceMode]. Unresolved
 * (authoring-time) steps define no view or navigation.
 */
sealed interface AudienceStep {
    val selection: QuestionSelection
    val resolved: List<Ref<Question>>?
    val mode: AudienceMode?
}

/** Query flag that makes the question page open on the group answer (a reveal). */
const val SHOW_GROUP_PARAM = "show=group"

/** Where followers go for an audience step: the question itself, or the room for several.
 *  A reveal lands on the group answer — and, being a different URL than the ask's,
 *  it also re-navigates followers who are already on the question. */
private fun AudienceStep.navigateUrl(room: Room): String? {
    val qs = resolved ?: return null
    if (mode == null || mode == AudienceMode.FREE || qs.isEmpty()) return null
    if (qs.size > 1) return room.urlPrefix
    val url = room.urlPrefix + Question.urlPrefix(qs.single().id)
    return if (this is RevealAnswersAction) "$url?$SHOW_GROUP_PARAM" else url
}

private fun AudienceStep.selectionProblem() = if (selection.isEmpty) "No questions selected" else null

/**
 * "Ask the audience": the projector shows the question(s), devices are pointed at
 * them, and — in [AudienceMode.ONE_AT_A_TIME] — the questions become visible and
 * open with their group answer hidden (see the server's executeFlowAction).
 *
 * Several questions are asked [oneByOne] or all at once, and the step can include
 * the reveal ([reveal], with the [RevealAnswersAction] switches) — the natural
 * "ask these questions" covers what would otherwise need a repeat loop. At Run the
 * step expands into its clicks ([expandClicks]); expanded clicks are plain
 * single-click asks and reveals.
 */
@Serializable
@SerialName("ask")
data class AskAudienceAction(
    override val selection: QuestionSelection = QuestionSelection(),
    val oneByOne: Boolean = true,
    val reveal: RevealTiming = RevealTiming.RIGHT_AFTER,
    /** As [RevealAnswersAction.stopAnswers], when this step reveals. */
    val stopAnswers: Boolean = true,
    /** As [RevealAnswersAction.showResolution], when this step reveals. */
    val showResolution: Boolean = false,
    override val resolved: List<Ref<Question>>? = null,
    override val mode: AudienceMode? = null,
) : FlowAction(), AudienceStep {
    override fun describe() = "Ask the audience: ${selection.describe()}"

    private fun revealOf(qs: List<Ref<Question>>) = RevealAnswersAction(
        QuestionSelection(questions = qs), stopAnswers, showResolution, resolved = qs, mode = mode)
    private fun askOf(qs: List<Ref<Question>>) = copy(
        selection = QuestionSelection(questions = qs), oneByOne = false, reveal = RevealTiming.LATER, resolved = qs)

    /**
     * The clicks this (resolved) step consists of, in order: a single-question,
     * no-reveal ask is just itself. Unresolved steps have no clicks yet.
     */
    fun expandClicks(): List<FlowAction> {
        val qs = resolved ?: return emptyList()
        if (qs.isEmpty()) return emptyList()
        val single = qs.size == 1 || !oneByOne
        return when {
            single && reveal == RevealTiming.LATER -> listOf(askOf(qs))
            single -> listOf(askOf(qs), revealOf(qs))
            reveal == RevealTiming.RIGHT_AFTER -> qs.flatMap { listOf(askOf(listOf(it)), revealOf(listOf(it))) }
            reveal == RevealTiming.AT_END -> qs.map { askOf(listOf(it)) } + revealOf(qs)
            else -> qs.map { askOf(listOf(it)) }
        }
    }

    override val presenterView: PresenterView? get() = resolved?.let { qs ->
        when (qs.size) {
            0 -> null
            1 -> QuestionPV(qs.single())
            else -> MultiQuestionPV(qs, showGroupPred = false)
        }
    }
    override fun getNavigateUrl(room: Room, lookup: EntityLookup) = navigateUrl(room)
    override fun questionRefs() = selection.questions + (resolved ?: emptyList())
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) =
        copy(selection = selection.mapQuestionRefs(f), resolved = resolved?.map(f))
    override fun configProblem() = selectionProblem()
}

/**
 * "Reveal answers": the projector shows the group answer(s); in
 * [AudienceMode.ONE_AT_A_TIME] the group answer becomes visible to everyone and,
 * per the switches, answering stops or the correct answer is shown (= resolved).
 */
@Serializable
@SerialName("reveal")
data class RevealAnswersAction(
    override val selection: QuestionSelection = QuestionSelection(),
    /** Close the question(s) to new answers (one-at-a-time mode only). */
    val stopAnswers: Boolean = true,
    /** Show the correct answer, where one is set — on the projector, and on devices
     *  in one-at-a-time mode (by resolving the question). */
    val showResolution: Boolean = false,
    override val resolved: List<Ref<Question>>? = null,
    override val mode: AudienceMode? = null,
) : FlowAction(), AudienceStep {
    override fun describe() = "Reveal answers: ${selection.describe()}"
    override val presenterView: PresenterView? get() = resolved?.let { qs ->
        when (qs.size) {
            0 -> null
            1 -> GroupPredPV(qs.single(), showResolution = showResolution)
            else -> MultiQuestionPV(qs, showGroupPred = true, showResolution = showResolution)
        }
    }
    override fun getNavigateUrl(room: Room, lookup: EntityLookup) = navigateUrl(room)
    override fun questionRefs() = selection.questions + (resolved ?: emptyList())
    override fun mapQuestionRefs(f: (Ref<Question>) -> Ref<Question>) =
        copy(selection = selection.mapQuestionRefs(f), resolved = resolved?.map(f))
    override fun configProblem() = selectionProblem()
}

/** Bind an audience step to its run: concrete questions (room order) and the mode. */
fun FlowAction.resolveAudienceStep(roomQuestions: List<Question>, mode: AudienceMode): FlowAction = when (this) {
    is AskAudienceAction -> copy(resolved = selection.resolve(roomQuestions).map { it.ref }, mode = mode)
    is RevealAnswersAction -> copy(resolved = selection.resolve(roomQuestions).map { it.ref }, mode = mode)
    else -> this
}
