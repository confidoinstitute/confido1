package components.redesign.rooms

import components.*
import components.questions.QuestionListProps
import components.redesign.*
import components.redesign.basic.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.*
import components.redesign.questions.dialog.*
import components.rooms.*
import csstype.*
import emotion.react.*
import hooks.*
import kotlinx.js.*
import react.*
import rooms.*
import tools.confido.question.*

external interface QuestionListProps : Props {
    var questions: List<Question>
    var showHiddenQuestions: Boolean
}

val QuestionList = FC<QuestionListProps> { props ->
    val (appState, _) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val groupPredsWS = useWebSocket<Map<String, Prediction?>>("/state${room.urlPrefix}/group_pred")
    val groupPreds = groupPredsWS.data ?: emptyMap()
    val layoutMode = useContext(LayoutModeContext)

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
        onPreset = {preset = it; presetOpen = false; editQuestionDialog(null)}
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
            width = 100.pct
            padding = if (layoutMode >= LayoutMode.TABLET) Padding(20.px, 0.px) else 20.px
            gap = 20.px
            maxWidth = layoutMode.contentWidth
            marginLeft = Auto.auto
            marginRight = Auto.auto
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
