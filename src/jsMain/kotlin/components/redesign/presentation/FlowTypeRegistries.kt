package components.redesign.presentation

import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.Checkbox
import components.redesign.forms.QuestionPicker
import components.redesign.forms.Select
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
    val onViewChange = wrapViewChange(props.onChange)
    // Set the room ref once when mounted (createDefault uses a placeholder Ref(""))
    useEffect(room.id) {
        val view = props.action.innerView() as? InviteLinkPV
        if (view != null && view.room.id != room.id) {
            onViewChange(InviteLinkPV(room.ref, "FIRST"))
        }
    }
    div {
        css { fontFamily = sansSerif; fontSize = 13.px; color = Color("#888"); marginTop = 4.px }
        +"Will show the room's invite link as a QR code on the presenter screen."
    }
}
