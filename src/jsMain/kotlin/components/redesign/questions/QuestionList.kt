package components.redesign.questions

import components.questions.QuestionListProps
import components.rooms.RoomContext
import csstype.*
import emotion.react.css
import hooks.useWebSocket
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useContext
import tools.confido.question.Prediction
import tools.confido.question.Question

external interface QuestionListProps : Props {
    var questions: List<Question>
    var showHiddenQuestions: Boolean
}

val QuestionList = FC<QuestionListProps> { props ->
    val room = useContext(RoomContext)
    val groupPredsWS = useWebSocket<Map<String, Prediction?>>("/state${room.urlPrefix}/group_pred")
    val groupPreds = groupPredsWS.data ?: emptyMap()

    val questions = props.questions.reversed()
    val visibleQuestions = if (props.showHiddenQuestions) questions else questions.filter { it.visible }

    div {
        css {
            boxSizing = BoxSizing.borderBox

            display = Display.flex;
            flexDirection = FlexDirection.column
            alignItems = AlignItems.stretch
            padding = 20.px
            gap = 20.px
            width = 100.pct
            position = Position.relative
        }

        visibleQuestions.map { question ->
            QuestionItem {
                this.question = question
                this.groupPred = groupPreds[question.id]
                this.onClick = {
                    console.log("Clicked on question $question")
                }
            }
        }
    }
}
