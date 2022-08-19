package components

import ReactPlotly
import mui.material.*
import react.*
import space.kscience.plotly.layout
import space.kscience.plotly.models.Scatter
import tools.confido.distributions.*

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

fun mark(value: Number, label: String?) = jsObject {
    this.value = value
    this.label = label
}


val NumericQuestion = FC<BinaryQuestionProps> {
    val question = it.question


    var mean by useState(50.0)
    var stdDev by useState(0.0)

    val dist = TruncatedNormalDistribution(mean, stdDev, 0.0, 100.0)

    Accordion {
        AccordionSummary {
            id = question.id
            Typography {
                + question.name
            }
        }
        AccordionDetails {
            fun confidence(p: Double) = Typography {
                val confidence = dist.confidenceInterval(1-p)
                + "You are ${p*100}% confident that the value lies between ${confidence.first} and ${confidence.second}"
            }

            ReactPlotly {
                id = question.id
                traces = listOf(
                    Scatter {
                        val xPoints = (0 .. 100).map {pt -> pt.toDouble()}
                        x.set(xPoints)
                        y.set(xPoints.map {pt -> dist.pdf(pt)})
                    }
                )
                plotlyInit = { plot ->
                    plot.layout {
                        this.yaxis {
                            this.visible = false
                        }
                    }
                }
            }

            + "Mean is $mean, Standard deviation is $stdDev"
            Slider {
                value = mean
                min = 0
                max = 100
                valueLabelDisplay = "auto"
                onChange = { _, value, _ -> mean = value}
            }
            Slider {
                value = stdDev
                min = 0
                max = 50
                valueLabelDisplay = "auto"
                onChange = { _, value, _ -> stdDev = value}
            }
            confidence(0.9)
            confidence(0.5)
            confidence(0.3)
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
        }
    }
}