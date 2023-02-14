package components.redesign.questions

import components.questions.QuestionListProps
import csstype.*
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import tools.confido.question.Question

external interface QuestionListProps : Props {
    var questions: List<Question>
    var showHiddenQuestions: Boolean
}

val QuestionList = FC<QuestionListProps> { props ->
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
                this.onClick = {
                    console.log("Clicked on question $question")
                }
            }
        }
    }
}
