package components

import mui.material.*
import react.*
import space.kscience.dataforge.values.asValue
import space.kscience.plotly.layout
import space.kscience.plotly.models.*
import tools.confido.distributions.*
import utils.*

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

            ReactPlotly {
                id = "${question.id}_distribution"
                traces = listOf(0.9, 0.5, 0.3).map { p ->
                    val (iMin, iMax) = dist.confidenceInterval(p)
                    Scatter {
                        val xPoints = (0..100).filter { pt -> iMin <= pt && pt <= iMax }.map { pt -> pt.toDouble() }
                        x.set(xPoints)
                        y.set(xPoints.map { pt -> dist.pdf(pt) })
                        mode = ScatterMode.lines
                    }
                }

                plotlyInit = { plot ->
                    plot.layout {
                        margin {
                            l = 0
                            r = 0
                            b = 25
                            t = 0
                        }
                        xaxis {
                            this.showline = false
                            this.visible = false
                            this.range(0.asValue(), 100.asValue())
                        }
                        yaxis {
                            this.showline = false
                            this.visible = false
                        }
                        height = 100
                        showlegend = false
                    }
                }
                config = jsObject {
                    staticPlot = true
                }
            }

            Slider {
                value = mean
                min = 0
                max = 100
                step = 0.1
                valueLabelDisplay = "auto"
                onChange = { _, value, _ -> mean = value}
            }
            Slider {
                value = stdDev
                min = 0
                max = 50
                step = 0.1
                valueLabelDisplay = "auto"
                onChange = { _, value, _ -> stdDev = value}
            }
            listOf(0.9,0.5,0.3).map {p ->
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
        }
    }
}