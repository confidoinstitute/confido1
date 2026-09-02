package tools.confido.web.components.redesign.presentation

import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.forms.Checkbox
import tools.confido.web.components.redesign.forms.QuestionPicker
import tools.confido.web.components.redesign.forms.Select
import tools.confido.web.components.redesign.forms.TextInput
import tools.confido.web.components.redesign.rooms.RoomContext
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.span
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.FlowItemCategory
import tools.confido.extensions.FlowItemEditorProps
import tools.confido.extensions.FlowItemTypeEntry
import tools.confido.question.GroupPredictionVisibility
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.*
import tools.confido.utils.capFirst
import web.cssom.*
import tools.confido.web.hooks.useSubscription
import tools.confido.payloads.responses.WSData
import tools.confido.state.Topics
import tools.confido.web.components.presenter.ExpectedWorldContext
import tools.confido.web.components.presenter.InviteLinkPC
import tools.confido.web.components.presenter.PresenterControlsType

// ============================================================
//  Unified Registry (populated lazily on first access)
// ============================================================

val flowItemTypes: List<FlowItemTypeEntry> by kotlin.lazy {
    buildList {
        // --- Side-effect actions ---
        add(FlowItemTypeEntry(
            key = "setQuestionState",
            label = "Set question state",
            category = FlowItemCategory.SIDE_EFFECT,
            createDefault = { SetQuestionStateAction(Ref(""), QuestionState.OPEN) },
            editor = SetQuestionStateEditor,
            matches = { it is SetQuestionStateAction },
        ))
        add(FlowItemTypeEntry(
            key = "setTaggedQuestionsState",
            label = "Set state for tagged questions",
            category = FlowItemCategory.SIDE_EFFECT,
            createDefault = { SetTaggedQuestionsStateAction("", QuestionState.OPEN) },
            editor = SetTaggedQuestionsStateEditor,
            matches = { it is SetTaggedQuestionsStateAction },
        ))
        add(FlowItemTypeEntry(
            key = "navigateToQuestion",
            label = "Navigate viewers to question",
            category = FlowItemCategory.SIDE_EFFECT,
            createDefault = { NavigateToQuestionAction(Ref("")) },
            editor = NavigateToQuestionEditor,
            matches = { it is NavigateToQuestionAction },
        ))
        add(FlowItemTypeEntry(
            key = "navigateViewers",
            label = "Navigate viewers to URL",
            category = FlowItemCategory.SIDE_EFFECT,
            createDefault = { NavigateViewersAction("") },
            editor = NavigateViewersEditor,
            matches = { it is NavigateViewersAction },
        ))
        add(FlowItemTypeEntry(
            key = "createMark",
            label = "Create timeline marker",
            category = FlowItemCategory.SIDE_EFFECT,
            createDefault = { CreateMarkAction("") },
            editor = CreateMarkActionEditor,
            matches = { it is CreateMarkAction },
        ))

        // --- Presenter view actions ---
        add(FlowItemTypeEntry(
            key = "empty",
            label = "Blank screen",
            category = FlowItemCategory.VIEW,
            createDefault = { SetPresenterViewAction(EmptyPV) },
            editor = null,
            matches = { it is SetPresenterViewAction && it.view is EmptyPV },
        ))
        add(FlowItemTypeEntry(
            key = "question",
            label = "Question text",
            category = FlowItemCategory.VIEW,
            createDefault = { SetPresenterViewAction(QuestionPV(Ref(""))) },
            editor = pvFlowEditor(questionSubject<QuestionPV>(
                get = { it.question }, set = { v, q -> v.copy(question = q) })),
            matches = { it is SetPresenterViewAction && it.view is QuestionPV },
        ))
        add(FlowItemTypeEntry(
            key = "groupPred",
            label = "Group predictions",
            category = FlowItemCategory.VIEW,
            createDefault = { SetPresenterViewAction(GroupPredPV(Ref(""))) },
            editor = pvFlowEditor(questionSubject<GroupPredPV>(
                get = { it.question }, set = { v, q -> v.copy(question = q) })),
            matches = { it is SetPresenterViewAction && it.view is GroupPredPV },
        ))
        add(FlowItemTypeEntry(
            key = "binaryHistogram",
            label = "Prediction histogram",
            category = FlowItemCategory.VIEW,
            createDefault = { SetPresenterViewAction(BinaryHistogramPV(Ref(""))) },
            editor = pvFlowEditor(questionSubject<BinaryHistogramPV>(
                filter = { q, _ -> q.answerSpace is tools.confido.spaces.BinarySpace },
                get = { it.question }, set = { v, q -> v.copy(question = q) })),
            matches = { it is SetPresenterViewAction && it.view is BinaryHistogramPV },
        ))
        add(FlowItemTypeEntry(
            key = "inviteLink",
            label = "Invite link / QR code",
            category = FlowItemCategory.VIEW,
            createDefault = { SetPresenterViewAction(InviteLinkPV(Ref(""), "FIRST")) },
            editor = pvFlowEditor(roomSubject<InviteLinkPV>(
                hint = "Will show the room's invite link as a QR code on the presenter screen.",
                get = { it.room }, set = { v, r -> v.copy(room = r.ref, id = "FIRST") }),
                // Authoring-only: joiners-follow is a property of the flow step,
                // not a live knob (see InviteLinkPC).
                extraControls = InviteLinkPC.unsafeCast<PresenterControlsType>()),
            matches = { it is SetPresenterViewAction && it.view is InviteLinkPV },
        ))
        add(FlowItemTypeEntry(
            key = "timeline",
            label = "Prediction timeline",
            category = FlowItemCategory.VIEW,
            createDefault = { SetPresenterViewAction(TimelinePV(Ref(""))) },
            editor = pvFlowEditor(questionSubject<TimelinePV>(
                get = { it.question }, set = { v, q -> v.copy(question = q) })),
            matches = { it is SetPresenterViewAction && it.view is TimelinePV },
        ))
        add(FlowItemTypeEntry(
            key = "compare",
            label = "Compare two moments",
            category = FlowItemCategory.VIEW,
            createDefault = { SetPresenterViewAction(ComparePV(Ref(""), MarkTP(""), NowTP)) },
            editor = pvFlowEditor(questionSubject<ComparePV>(
                get = { it.question }, set = { v, q -> v.copy(question = q) })),
            matches = { it is SetPresenterViewAction && it.view is ComparePV },
        ))

        // --- Extension-registered types ---
        ClientExtension.forEach { addAll(it.registerFlowItemTypes()) }
    }
}

// ============================================================
//  Helpers (shared by core editors)
// ============================================================

/** Resolve the room's questions from the room questions slice. Real hook —
 *  call unconditionally within an FC. */
fun useRoomQuestions(): List<tools.confido.question.Question> {
    val room = use(RoomContext)!!
    val qmap = (useSubscription(Topics.roomQuestions(room.ref)) as? WSData)?.data ?: emptyMap()
    return room.questions.mapNotNull { qmap[it.id] }.sortedBy { it.name }
}

/** All tags from the room's questions. Must be called within an FC. */
fun useRoomTags(): List<String> {
    val questions = useRoomQuestions()
    return questions.flatMap { it.tags }.distinct().sorted()
}

// ---- Small reusable form bits ----

val FieldLabel = FC<PropsWithChildren> { props ->
    div {
        css { fontFamily = sansSerif; fontSize = 13.px; fontWeight = integer(600); color = Color("#666"); marginTop = 4.px }
        +props.children
    }
}

private external interface QuestionStateSelectProps : Props {
    var value: QuestionState
    var onChange: (QuestionState) -> Unit
}

private val QuestionStateSelect = FC<QuestionStateSelectProps> { props ->
    Select {
        value = props.value.name
        onChange = { e ->
            QuestionState.entries.find { it.name == e.target.value }?.let { props.onChange(it) }
        }
        css { width = 100.pct }
        QuestionState.entries.forEach { qs ->
            option { value = qs.name; +qs.name.lowercase().capFirst() }
        }
    }
}

private external interface GroupPredVisibilitySelectProps : Props {
    /** null means "No change". */
    var value: GroupPredictionVisibility?
    var onChange: (GroupPredictionVisibility?) -> Unit
}

private val GroupPredVisibilitySelect = FC<GroupPredVisibilitySelectProps> { props ->
    Select {
        value = props.value?.name ?: ""
        onChange = { e ->
            val v = e.target.value
            props.onChange(if (v.isEmpty()) null else GroupPredictionVisibility.entries.find { it.name == v })
        }
        css { width = 100.pct }
        option { value = ""; +"No change" }
        option { value = GroupPredictionVisibility.EVERYONE.name; +"Everyone" }
        option { value = GroupPredictionVisibility.ANSWERED.name; +"Answered only" }
        option { value = GroupPredictionVisibility.MODERATOR_ONLY.name; +"Moderator only (hidden)" }
    }
}

// ============================================================
//  Core Side-Effect Action Editors
// ============================================================

/** Grey (or amber, when [warning]) reference line giving expected-at-this-step
 *  context from the [ExpectedWorldContext] simulation. */
private fun ChildrenBuilder.expectedStateLine(text: String, warning: Boolean = false) {
    div {
        css {
            fontFamily = sansSerif; fontSize = 12.px; marginTop = 2.px
            color = if (warning) Color("#b26a00") else Color("#888")
        }
        +text
    }
}

private fun QuestionState.describeLower() = name.lowercase()

/** Editor for [SetQuestionStateAction]. */
val SetQuestionStateEditor = FC<FlowItemEditorProps> { props ->
    val questions = useRoomQuestions()
    val world = use(ExpectedWorldContext)
    val action = props.action as? SetQuestionStateAction ?: return@FC

    FieldLabel { +"Question" }
    QuestionPicker {
        this.questions = questions
        selectedId = action.question.id
        currentQuestionOption = world?.loopCandidates != null
        onChange = { props.onChange(action.copy(question = Ref(it))) }
    }
    FieldLabel { +"New state" }
    QuestionStateSelect {
        value = action.newState
        onChange = { props.onChange(action.copy(newState = it)) }
    }
    world?.question(action.question)?.let { expected ->
        val noop = expected.state == action.newState
        expectedStateLine(
            "Currently ${expected.state.describeLower()} at this point in the flow" +
                if (noop) " — this step changes nothing." else ".",
            warning = noop)
    }
    if (action.question == CURRENT_QUESTION) world?.loopCandidates?.takeIf { it.isNotEmpty() }?.let { cand ->
        val states = cand.groupingBy { it.state }.eachCount()
            .entries.joinToString(", ") { (s, n) -> "$n ${s.describeLower()}" }
        expectedStateLine("The loop's questions are currently $states at this point in the flow.")
    }
    // Context-sensitive navigation checkbox:
    // - Draft (hiding): offer "navigate back to room"
    // - Other states: offer "navigate to this question"
    val isDraft = action.newState == QuestionState.DRAFT
    val navTarget = if (isDraft) ViewerNavigateTarget.TO_ROOM else ViewerNavigateTarget.TO_QUESTION
    val isChecked = action.navigateViewers == navTarget
    Stack {
        direction = FlexDirection.row
        css { alignItems = AlignItems.center; gap = 8.px; marginTop = 4.px }
        Checkbox {
            checked = isChecked
            onChange = { e ->
                props.onChange(action.copy(
                    navigateViewers = if (e.target.checked) navTarget else ViewerNavigateTarget.NONE
                ))
            }
        }
        span {
            css { fontFamily = sansSerif; fontSize = 14.px }
            if (isDraft) +"Navigate viewers back to room" else +"Navigate viewers to this question"
        }
    }
    FieldLabel { +"Group prediction visibility" }
    GroupPredVisibilitySelect {
        value = action.setGroupPredVisibility
        onChange = { props.onChange(action.copy(setGroupPredVisibility = it)) }
    }
}

/** Editor for [SetTaggedQuestionsStateAction]. */
val SetTaggedQuestionsStateEditor = FC<FlowItemEditorProps> { props ->
    val questions = useRoomQuestions()
    val world = use(ExpectedWorldContext)
    val action = props.action as? SetTaggedQuestionsStateAction ?: return@FC
    val allTags = questions.flatMap { it.tags }.distinct().sorted()

    FieldLabel { +"Tag" }
    if (allTags.isNotEmpty()) {
        Select {
            value = action.tag
            onChange = { e -> props.onChange(action.copy(tag = e.target.value)) }
            css { width = 100.pct }
            if (action.tag.isEmpty()) {
                option { value = ""; +"Select a tag..." }
            }
            allTags.forEach { tag ->
                option { value = tag; +tag }
            }
        }
    } else {
        div {
            css { fontFamily = sansSerif; fontSize = 13.px; color = Color("#999") }
            +"No tags defined on questions in this room."
        }
    }
    if (world != null && action.tag.isNotBlank()) {
        val matching = questions.filter { action.tag in it.tags }.map { world.question(it.ref) ?: it }
        if (matching.isEmpty()) expectedStateLine("No questions carry this tag.", warning = true)
        else {
            val states = matching.groupingBy { it.state }.eachCount()
                .entries.joinToString(", ") { (s, n) -> "$n ${s.describeLower()}" }
            expectedStateLine("Affects ${matching.size} " +
                (if (matching.size == 1) "question" else "questions") +
                " — currently $states at this point in the flow.")
        }
    }
    FieldLabel { +"New state" }
    QuestionStateSelect {
        value = action.newState
        onChange = { props.onChange(action.copy(newState = it)) }
    }
    Stack {
        direction = FlexDirection.row
        css { alignItems = AlignItems.center; gap = 8.px; marginTop = 4.px }
        Checkbox {
            checked = action.hideOtherQuestions
            onChange = { e -> props.onChange(action.copy(hideOtherQuestions = e.target.checked)) }
        }
        span {
            css { fontFamily = sansSerif; fontSize = 14.px }
            +"Hide all other questions"
        }
    }
    FieldLabel { +"Group prediction visibility" }
    GroupPredVisibilitySelect {
        value = action.setGroupPredVisibility
        onChange = { props.onChange(action.copy(setGroupPredVisibility = it)) }
    }
    val isDraft = action.newState == QuestionState.DRAFT
    FieldLabel { +"Navigate viewers" }
    Select {
        value = action.navigateViewers.name
        onChange = { e ->
            ViewerNavigateTarget.entries.find { it.name == e.target.value }?.let {
                props.onChange(action.copy(navigateViewers = it))
            }
        }
        css { width = 100.pct }
        option { value = ViewerNavigateTarget.NONE.name; +"No navigation" }
        option { value = ViewerNavigateTarget.TO_ROOM.name; +"Navigate to room" }
        if (!isDraft) {
            option { value = ViewerNavigateTarget.TO_FIRST_QUESTION.name; +"Navigate to first tagged question" }
        }
    }
}

// ============================================================
//  Navigation Action Editors
// ============================================================

/** Editor for [NavigateToQuestionAction]: question picker. */
val NavigateToQuestionEditor = FC<FlowItemEditorProps> { props ->
    val questions = useRoomQuestions()
    val world = use(ExpectedWorldContext)
    val action = props.action as? NavigateToQuestionAction ?: return@FC

    FieldLabel { +"Question" }
    QuestionPicker {
        this.questions = questions
        selectedId = action.question.id
        currentQuestionOption = world?.loopCandidates != null
        onChange = { props.onChange(action.copy(question = Ref(it))) }
    }
    world?.question(action.question)?.let { expected ->
        val hidden = expected.state == QuestionState.DRAFT
        expectedStateLine(
            "This question is ${expected.state.describeLower()} at this point in the flow" +
                if (hidden) " — viewers will not be able to see it!" else ".",
            warning = hidden)
    }
    if (action.question == CURRENT_QUESTION) world?.loopCandidates?.takeIf { it.isNotEmpty() }?.let { cand ->
        val drafts = cand.count { it.state == QuestionState.DRAFT }
        expectedStateLine(
            "Navigates to each iteration's question." +
                if (drafts > 0) " $drafts of the loop's questions are still drafts at this point!" else "",
            warning = drafts > 0)
    }
}

/** Editor for [NavigateViewersAction]: URL text input. */
val NavigateViewersEditor = FC<FlowItemEditorProps> { props ->
    val action = props.action as? NavigateViewersAction ?: return@FC

    FieldLabel { +"URL" }
    TextInput {
        value = action.url
        placeholder = "/rooms/abc123"
        onChange = { e -> props.onChange(action.copy(url = e.target.value)) }
    }
    div {
        css { fontFamily = sansSerif; fontSize = 12.px; color = Color("#999"); marginTop = 2.px }
        +"All presentation followers will be navigated to this URL."
    }
}

/** Editor for [CreateMarkAction]: name + scope (room-wide or one question). */
val CreateMarkActionEditor = FC<FlowItemEditorProps> { props ->
    val questions = useRoomQuestions()
    val room = use(RoomContext)!!
    val world = use(ExpectedWorldContext)
    val action = props.action as? CreateMarkAction ?: return@FC

    FieldLabel { +"Marker name" }
    TextInput {
        value = action.name
        placeholder = "e.g. Before discussion"
        onChange = { e -> props.onChange(action.copy(name = e.target.value)) }
    }
    // Non-blocking duplicate hints: overwrite-by-name is intended semantics
    // (a re-run flow moves its mark), but surface it so accidental collisions
    // with earlier steps or hand-made markers don't surprise anyone.
    if (action.name.isNotBlank()) {
        val flowDup = world?.marks?.firstOrNull { it.name == action.name }
        val existing = (room.timelineMarks +
            (action.question?.let { qr -> questions.firstOrNull { it.id == qr.id } }?.timelineMarks ?: emptyList()))
            .firstOrNull { it.name == action.name }
        when {
            flowDup != null -> expectedStateLine(
                "Also created at step ${flowDup.stepIndex + 1} of this flow — this step will move it.",
                warning = true)
            existing != null -> expectedStateLine(
                "A marker with this name already exists — running this step will move it.",
                warning = true)
        }
    }
    FieldLabel { +"Scope" }
    Select {
        value = if (action.question != null) "question" else "room"
        onChange = { e ->
            when (e.target.value) {
                "room" -> props.onChange(action.copy(question = null))
                // Inside a loop body, question scope defaults to the current question
                // (a room-wide mark would be overwritten by every iteration).
                "question" -> props.onChange(action.copy(question =
                    if (world?.loopCandidates != null) CURRENT_QUESTION
                    else Ref(questions.firstOrNull()?.id ?: "")))
            }
        }
        css { width = 100.pct }
        option { value = "room"; +"Room-wide" }
        option { value = "question"; +"Specific question" }
    }
    if (action.question != null) {
        FieldLabel { +"Question" }
        QuestionPicker {
            this.questions = questions
            selectedId = action.question!!.id
            currentQuestionOption = world?.loopCandidates != null
            onChange = { props.onChange(action.copy(question = Ref(it))) }
        }
    }
    if (world?.loopCandidates != null && action.question == null)
        expectedStateLine("Room-wide markers are overwritten by each iteration — " +
            "scope the marker to the current question inside a loop.", warning = true)
}

