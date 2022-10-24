package components.questions

import components.*
import components.rooms.RoomContext
import icons.AddIcon
import mui.material.*
import react.*
import rooms.RoomPermission
import tools.confido.question.*
import tools.confido.refs.ref
import tools.confido.utils.*

external interface QuestionListProps : Props {
    var questions: List<Question>
    var showHiddenQuestions: Boolean
    var allowEditingQuestions: Boolean
}

val QuestionList = FC<QuestionListProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val questions = props.questions.sortedBy { it.name }
    val visibleQuestions = if (props.showHiddenQuestions) questions else questions.filter { it.visible }

    var editQuestion by useState<Question?>(null)
    var editQuestionKey by useState("")
    var editOpen by useState(false)
    useLayoutEffect(editOpen) {
        if (editOpen)
            editQuestionKey = randomString(20)
    }

    var expandedQuestion by useState<String?>(null)

    EditQuestionDialog {
        key = "##editDialog##$editQuestionKey"
        question = editQuestion
        open = editOpen
        onClose = { editOpen = false }
    }

    fun editQuestionOpen(it: Question) {
        editQuestion = it; editOpen = true
    }

    val canPredict = appState.hasPermission(room, RoomPermission.SUBMIT_PREDICTION)
    visibleQuestions.map { question ->
        QuestionItem {
            this.key = question.id
            this.question = question
            this.expanded = question.id == expandedQuestion
            this.prediction = appState.myPredictions[question.ref]
            this.editable = props.allowEditingQuestions
            this.canPredict = canPredict
            this.comments = appState.questionComments[question.ref] ?: emptyMap()
            this.onEditDialog = ::editQuestionOpen
            this.onChange = {state -> expandedQuestion = if (state) question.id else null}
        }
    }

    if (appState.hasPermission(room, RoomPermission.ADD_QUESTION)) {
        Fragment {
            Button {
                this.key = "##add##"
                this.startIcon = AddIcon.create()
                this.color = ButtonColor.primary
                this.disabled = stale
                onClick = { editQuestion = null; editOpen = true }
                +"Add question…"
            }
        }
    }
}
