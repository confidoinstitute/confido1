package components

import mui.material.*
import react.*
import space.kscience.dataforge.values.asValue
import space.kscience.plotly.layout
import space.kscience.plotly.models.*
import tools.confido.distributions.*
import tools.confido.question.Question
import utils.*


external interface BinaryQuestionProps : Props {
    var question : Question
}

fun mark(value: Number, label: String?) = jsObject {
    this.value = value
    this.label = label
}


val NumericQuestion = FC<BinaryQuestionProps> {
    val question = it.question

    var mean by useState(50.0)
    var stdDev by useState(0.0)

    val dist = TruncatedNormalDistribution(mean, stdDev, 0.0, 100.0)

    fun sendPrediction() {
        console.log("sending mean $mean and standard deviation $stdDev")
    }

    Accordion {
        AccordionSummary {
            id = question.id
            Typography {
                + question.name
            }
        }
        AccordionDetails {

            DistributionPlot {
                id = "${question.id}_plot"
                min = 0.0
                max = 100.0
                step = 0.5
                distribution = dist
                confidences = listOf(ConfidenceColor(0.9, "#333333".asValue()), ConfidenceColor(0.7, "#666666".asValue()), ConfidenceColor(0.5, "#999999".asValue()))
            }

            Slider {
                value = mean
                min = 0
                max = 100
                step = 0.1
                valueLabelDisplay = "auto"
                onChange = { _, value, _ -> mean = value}
                onChangeCommitted = { _,_ -> sendPrediction() }
            }
            Slider {
                value = stdDev
                min = 0
                max = 50
                step = 0.1
                valueLabelDisplay = "auto"
                onChange = { _, value, _ -> stdDev = value}
                onChangeCommitted = { _,_ -> sendPrediction() }
            }
            listOf(0.9,0.7,0.5).map {p ->
                Typography {
                    val confidence = dist.confidenceInterval(1 - p)
                    +"You are ${p * 100}% confident that the value lies between ${confidence.first.format(1)} and ${confidence.second.format(1)}"
                }
            }
        }
    }

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
                marks = arrayOf(
                    mark(0, "0 %"),
                    mark(100, "100 %"),
                )
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
        NumericQuestion {
            this.question = question
            this.key = question.id
        }
    }
}