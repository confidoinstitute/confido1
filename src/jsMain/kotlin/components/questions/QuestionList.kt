package components.questions

import components.*
import components.rooms.CsvExportDialog
import components.rooms.RoomContext
import hooks.useEditDialog
import icons.AddIcon
import mui.material.*
import react.*
import rooms.RoomPermission
import tools.confido.question.*
import tools.confido.refs.ref

external interface QuestionListProps : Props {
    var questions: List<Question>
    var showHiddenQuestions: Boolean
    var allowEditingQuestions: Boolean
}

val QuestionList = FC<QuestionListProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val questions = props.questions.reversed()
    val visibleQuestions = if (props.showHiddenQuestions) questions else questions.filter { it.visible }

    var expandedQuestions by useState<Set<String>>(emptySet())

    val editQuestionOpen = useEditDialog(EditQuestionDialog)

    val canPredict = appState.hasPermission(room, RoomPermission.SUBMIT_PREDICTION)
    visibleQuestions.map { question ->
        QuestionItem {
            this.key = question.id
            this.question = question
            this.expanded = question.id in expandedQuestions
            this.prediction = appState.myPredictions[question.ref]
            this.editable = props.allowEditingQuestions
            this.canPredict = canPredict
            this.commentCount = appState.commentCount[question.ref] ?: 0
            this.onEditDialog = editQuestionOpen
            this.onChange = { state ->
                if (state) {
                    expandedQuestions += question.id
                } else {
                    expandedQuestions -= question.id
                }
            }
        }
    }

    if (appState.hasPermission(room, RoomPermission.ADD_QUESTION)) {
        Fragment {
            Button {
                this.key = "##add##"
                this.startIcon = AddIcon.create()
                this.color = ButtonColor.primary
                this.disabled = stale
                onClick = { editQuestionOpen(null) }
                +"Add question…"
            }
        }
    }
    if (appState.hasAnyPermission(room, RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)) {
        CsvExportDialog {

        }
    }
}
