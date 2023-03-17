package components.redesign.rooms

import components.AppStateContext
import components.questions.QuestionListProps
import components.redesign.SortButton
import components.redesign.SortType
import components.redesign.basic.Stack
import components.redesign.questions.dialog.EditQuestionDialog
import components.redesign.questions.QuestionItem
import components.redesign.questions.dialog.AddQuestionPresetDialog
import components.redesign.questions.dialog.QuestionPreset
import components.rooms.RoomContext
import csstype.*
import emotion.react.css
import hooks.useEditDialog
import hooks.useWebSocket
import kotlinx.js.jso
import react.FC
import react.Props
import react.useContext
import react.useState
import rooms.RoomPermission
import tools.confido.question.Prediction
import tools.confido.question.Question

external interface QuestionListProps : Props {
    var questions: List<Question>
    var showHiddenQuestions: Boolean
}

val QuestionList = FC<QuestionListProps> { props ->
    val (appState, _) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val groupPredsWS = useWebSocket<Map<String, Prediction?>>("/state${room.urlPrefix}/group_pred")
    val groupPreds = groupPredsWS.data ?: emptyMap()

    var sortType by useState(SortType.SET_BY_MODERATOR)

    val questions = when (sortType) {
        SortType.SET_BY_MODERATOR -> props.questions.reversed()
        SortType.NEWEST -> emptyList() // TODO: backend support
        SortType.OLDEST -> emptyList() // TODO: backend support
    }
    val visibleQuestions = if (props.showHiddenQuestions) questions else questions.filter { it.visible }

    var preset by useState(QuestionPreset.NONE)
    var presetOpen by useState(false)

    val editQuestionDialog = useEditDialog(EditQuestionDialog, jso {
        this.preset = preset
    })

    fun addQuestion() {
        presetOpen = true
    }

    AddQuestionPresetDialog {
        open = presetOpen
        onClose = {presetOpen = false}
        onPreset = {preset = it; editQuestionDialog(null)}
    }

    RoomHeader {
        SortButton {
            options = listOf(SortType.SET_BY_MODERATOR)
            this.sortType = sortType
            onChange = { sort -> sortType = sort }
        }

        if (appState.hasPermission(room, RoomPermission.ADD_QUESTION)) {
            RoomHeaderButton {
                +"Create a question"
                onClick = { addQuestion() }
            }
        }
    }

    Stack {
        css {
            flexGrow = number(1.0)

            alignItems = AlignItems.stretch
            padding = 20.px
            gap = 20.px
            width = 100.pct
            position = Position.relative
            backgroundColor = Color("#f2f2f2")
        }

        visibleQuestions.map { question ->
            QuestionItem {
                this.key = question.id
                this.question = question
                this.groupPred = groupPreds[question.id]
                this.href = room.urlPrefix+question.urlPrefix
            }
        }
    }
}
