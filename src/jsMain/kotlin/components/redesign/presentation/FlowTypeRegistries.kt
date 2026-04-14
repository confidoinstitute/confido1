package components.redesign.presentation

import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.Checkbox
import components.redesign.forms.QuestionPicker
import components.redesign.forms.Select
import components.redesign.forms.TextInput
import components.redesign.rooms.RoomContext
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
            editor = QuestionPVEditor,
            matches = { it is SetPresenterViewAction && it.view is QuestionPV },
        ))
        add(FlowItemTypeEntry(
            key = "groupPred",
            label = "Group predictions",
            category = FlowItemCategory.VIEW,
            createDefault = { SetPresenterViewAction(GroupPredPV(Ref(""))) },
            editor = GroupPredPVEditor,
            matches = { it is SetPresenterViewAction && it.view is GroupPredPV },
        ))
        add(FlowItemTypeEntry(
            key = "inviteLink",
            label = "Invite link / QR code",
            category = FlowItemCategory.VIEW,
            createDefault = { SetPresenterViewAction(InviteLinkPV(Ref(""), "FIRST")) },
            editor = InviteLinkPVEditor,
            matches = { it is SetPresenterViewAction && it.view is InviteLinkPV },
        ))

        // --- Extension-registered types ---
        ClientExtension.forEach { addAll(it.registerFlowItemTypes()) }
    }
}

// ============================================================
//  Helpers (shared by core editors)
// ============================================================

/** Resolve the room's questions from context. Must be called within an FC. */
fun useRoomQuestions(): List<tools.confido.question.Question> {
    val (appState, _) = use(AppStateContext)!!
    val room = use(RoomContext)!!
    return appState.questions.values.filter { q ->
        room.questions.any { it.id == q.id }
    }.sortedBy { it.name }
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
//  Helpers for presenter view editors (wrap/unwrap SetPresenterViewAction)
// ============================================================

/** Extract the inner [PresenterView] from a [SetPresenterViewAction], or null. */
private fun FlowAction.innerView(): PresenterView? = (this as? SetPresenterViewAction)?.view

/** Wrap a [PresenterView] change back into the [SetPresenterViewAction]. */
private fun wrapViewChange(onChange: (FlowAction) -> Unit): (PresenterView) -> Unit =
    { view -> onChange(SetPresenterViewAction(view)) }

// ============================================================
//  Core Side-Effect Action Editors
// ============================================================

/** Editor for [SetQuestionStateAction]. */
val SetQuestionStateEditor = FC<FlowItemEditorProps> { props ->
    val action = props.action as? SetQuestionStateAction ?: return@FC
    val questions = useRoomQuestions()

    FieldLabel { +"Question" }
    QuestionPicker {
        this.questions = questions
        selectedId = action.question.id
        onChange = { props.onChange(action.copy(question = Ref(it))) }
    }
    FieldLabel { +"New state" }
    QuestionStateSelect {
        value = action.newState
        onChange = { props.onChange(action.copy(newState = it)) }
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
    val action = props.action as? SetTaggedQuestionsStateAction ?: return@FC
    val allTags = useRoomTags()

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
//  Core Presenter View Editors (wrapped as FlowItemEditorProps)
// ============================================================

/** Editor for [QuestionPV]: just a question picker. */
val QuestionPVEditor = FC<FlowItemEditorProps> { props ->
    val view = props.action.innerView() as? QuestionPV ?: return@FC
    val questions = useRoomQuestions()
    val onViewChange = wrapViewChange(props.onChange)

    FieldLabel { +"Question" }
    QuestionPicker {
        this.questions = questions
        selectedId = view.question.id
        onChange = { onViewChange(QuestionPV(Ref(it))) }
    }
}

/** Editor for [GroupPredPV]: question picker + showResolution toggle. */
val GroupPredPVEditor = FC<FlowItemEditorProps> { props ->
    val view = props.action.innerView() as? GroupPredPV ?: return@FC
    val questions = useRoomQuestions()
    val onViewChange = wrapViewChange(props.onChange)

    FieldLabel { +"Question" }
    QuestionPicker {
        this.questions = questions
        selectedId = view.question.id
        onChange = { onViewChange(view.copy(question = Ref(it))) }
    }
    Stack {
        direction = FlexDirection.row
        css { alignItems = AlignItems.center; gap = 8.px; marginTop = 4.px }
        Checkbox {
            checked = view.showResolution
            onChange = { e -> onViewChange(view.copy(showResolution = e.target.checked)) }
        }
        span {
            css { fontFamily = sansSerif; fontSize = 14.px }
            +"Show resolution"
        }
    }
}

/** Editor for [InviteLinkPV]: informational text, sets room ref from context on mount. */
val InviteLinkPVEditor = FC<FlowItemEditorProps> { props ->
    val room = use(RoomContext)!!
    val view = props.action.innerView() as? InviteLinkPV ?: return@FC
    val onViewChange = wrapViewChange(props.onChange)
    // Set the room ref once when mounted (createDefault uses a placeholder Ref(""))
    useEffect(room.id) {
        if (view.room.id != room.id) {
            onViewChange(InviteLinkPV(room.ref, "FIRST", followPresentation = view.followPresentation))
        }
    }
    div {
        css { fontFamily = sansSerif; fontSize = 13.px; color = Color("#888"); marginTop = 4.px }
        +"Will show the room's invite link as a QR code on the presenter screen."
    }
    Stack {
        direction = FlexDirection.row
        css { alignItems = AlignItems.center; gap = 8.px; marginTop = 4.px }
        Checkbox {
            checked = view.followPresentation
            onChange = { e -> onViewChange(view.copy(followPresentation = e.target.checked)) }
        }
        span {
            css { fontFamily = sansSerif; fontSize = 14.px }
            +"Users joining via this link will follow the presentation"
        }
    }
}

// ============================================================
//  Navigation Action Editors
// ============================================================

/** Editor for [NavigateToQuestionAction]: question picker. */
val NavigateToQuestionEditor = FC<FlowItemEditorProps> { props ->
    val action = props.action as? NavigateToQuestionAction ?: return@FC
    val questions = useRoomQuestions()

    FieldLabel { +"Question" }
    QuestionPicker {
        this.questions = questions
        selectedId = action.question.id
        onChange = { props.onChange(action.copy(question = Ref(it))) }
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
