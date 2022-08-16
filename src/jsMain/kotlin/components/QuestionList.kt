package components

import mui.material.*
import react.*

inline fun jsObject(init: dynamic.() -> Unit): dynamic {
    val o = js("{}")
    init(o)
    return o
}

data class Question(
    val id: String,
    val name: String,
    val visible: Boolean,
)

external interface BinaryQuestionProps : Props {
    var question : Question
}

val BinaryQuestion = FC<BinaryQuestionProps> {
    val question = it.question

    fun formatPercent(value: Int): String = "$value %"

    Accordion {
        AccordionSummary {
            id = question.id
            Typography {
                + question.name
            }
        }
        AccordionDetails {
            Slider {
                defaultValue = 50
                min = 0
                max = 100
                valueLabelDisplay = "auto"
                valueLabelFormat = ::formatPercent
                // TODO find Kotlin typesafe way to implement this
                marks = js("[{label: '0%',value: 0}, {label: '100%', value:100}]")
            }
        }
    }
}

external interface QuestionListProps : Props {
    var questions : List<Question>
}

val QuestionList = FC<QuestionListProps> {props ->
    val visibleQuestions = props.questions.filter { it.visible }

    visibleQuestions.map {question ->
        BinaryQuestion {
            this.question = question
        }
    }
}