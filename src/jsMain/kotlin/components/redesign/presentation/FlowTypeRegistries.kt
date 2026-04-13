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
import tools.confido.extensions.FlowActionEditorProps
import tools.confido.extensions.FlowActionTypeEntry
import tools.confido.extensions.PresenterViewEditorProps
import tools.confido.extensions.PresenterViewTypeEntry
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.*
import tools.confido.utils.capFirst
import web.cssom.*

// ============================================================
//  Registries (populated lazily on first access)
// ============================================================

val flowActionTypes: List<FlowActionTypeEntry> by kotlin.lazy {
    buildList {
        add(FlowActionTypeEntry(
            key = "setQuestionState",
            label = "Set question state",
            createDefault = { SetQuestionStateAction(Ref(""), QuestionState.OPEN) },
            editor = SetQuestionStateEditor,
            matches = { it is SetQuestionStateAction },
        ))
        add(FlowActionTypeEntry(
            key = "setTaggedQuestionsState",
            label = "Set state for tagged questions",
            createDefault = { SetTaggedQuestionsStateAction("", QuestionState.OPEN) },
            editor = SetTaggedQuestionsStateEditor,
            matches = { it is SetTaggedQuestionsStateAction },
        ))
        ClientExtension.forEach { addAll(it.registerFlowActionTypes()) }
    }
}

val presenterViewTypes: List<PresenterViewTypeEntry> by kotlin.lazy {
    buildList {
        add(PresenterViewTypeEntry(
            key = "empty",
            label = "Blank screen",
            createDefault = { EmptyPV },
            editor = null,
            matches = { it is EmptyPV },
        ))
        add(PresenterViewTypeEntry(
            key = "question",
            label = "Question text",
            createDefault = { QuestionPV(Ref("")) },
            editor = QuestionPVEditor,
            matches = { it is QuestionPV },
        ))
        add(PresenterViewTypeEntry(
            key = "groupPred",
            label = "Group predictions",
            createDefault = { GroupPredPV(Ref("")) },
            editor = GroupPredPVEditor,
            matches = { it is GroupPredPV },
        ))
        add(PresenterViewTypeEntry(
            key = "inviteLink",
            label = "Invite link / QR code",
            createDefault = { InviteLinkPV(Ref(""), "FIRST") },
            editor = InviteLinkPVEditor,
            matches = { it is InviteLinkPV },
        ))
        ClientExtension.forEach { addAll(it.registerPresenterViewTypes()) }
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
//  Core Flow Action Editors
// ============================================================

/** Editor for [SetQuestionStateAction]. */
val SetQuestionStateEditor = FC<FlowActionEditorProps> { props ->
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
val SetTaggedQuestionsStateEditor = FC<FlowActionEditorProps> { props ->
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
//  Core Presenter View Editors
// ============================================================

/** Editor for [QuestionPV]: just a question picker. */
val QuestionPVEditor = FC<PresenterViewEditorProps> { props ->
    val view = props.view as? QuestionPV ?: return@FC
    val questions = useRoomQuestions()

    FieldLabel { +"Question" }
    QuestionPicker {
        this.questions = questions
        selectedId = view.question.id
        onChange = { props.onChange(QuestionPV(Ref(it))) }
    }
}

/** Editor for [GroupPredPV]: question picker + showResolution toggle. */
val GroupPredPVEditor = FC<PresenterViewEditorProps> { props ->
    val view = props.view as? GroupPredPV ?: return@FC
    val questions = useRoomQuestions()

    FieldLabel { +"Question" }
    QuestionPicker {
        this.questions = questions
        selectedId = view.question.id
        onChange = { props.onChange(view.copy(question = Ref(it))) }
    }
    Stack {
        direction = FlexDirection.row
        css { alignItems = AlignItems.center; gap = 8.px; marginTop = 4.px }
        Checkbox {
            checked = view.showResolution
            onChange = { e -> props.onChange(view.copy(showResolution = e.target.checked)) }
        }
        span {
            css { fontFamily = sansSerif; fontSize = 14.px }
            +"Show resolution"
        }
    }
}

/** Editor for [InviteLinkPV]: informational text, sets room ref from context on mount. */
val InviteLinkPVEditor = FC<PresenterViewEditorProps> { props ->
    val room = use(RoomContext)!!
    // Set the room ref once when mounted (createDefault uses a placeholder Ref(""))
    useEffect(room.id) {
        val view = props.view as? InviteLinkPV
        if (view != null && view.room.id != room.id) {
            props.onChange(InviteLinkPV(room.ref, "FIRST"))
        }
    }
    div {
        css { fontFamily = sansSerif; fontSize = 13.px; color = Color("#888"); marginTop = 4.px }
        +"Will show the room's invite link as a QR code on the presenter screen."
    }
}
