package components

import csstype.px
import emotion.react.css
import mui.material.*
import react.*
import space.kscience.plotly.layout
import space.kscience.plotly.models.Bar
import space.kscience.plotly.models.Pie
import tools.confido.question.*
import utils.jsObject

external interface PredictionPlotProps : Props {
    var question: Question
    var histogram: List<Double>
}

val PredictionPlot = FC<PredictionPlotProps> {props ->
    val config = jsObject {
        this.responsive = true
    }
    Card {
        raised = true


        CardHeader {
            title = ReactNode(props.question.name)
        }
        CardContent {
            css {
                height = 500.px
            }
            when(props.question.answerSpace) {
                is BinaryAnswerSpace -> ReactPlotly {
                    if (props.histogram.size != 2)
                        error("This is not a correct size")
                    traces = listOf(
                        Pie {
                            labels(listOf("No", "Yes"))
                            values(props.histogram)
                        }
                    )
                    this.config = config
                }
                is NumericAnswerSpace -> ReactPlotly {
                    traces = listOf(
                        Bar {
                            x.set(0 until props.histogram.size)
                            y.set(props.histogram)
                        }
                    )
                    plotlyInit = { plot ->
                        plot.layout {
                            bargap = 0
                        }
                    }

                    this.config = config
                }
            }
        }
    }
}

external interface GroupPredictionsProps : Props {
    var predictions: List<Pair<Question, List<Double>>>
}

val GroupPredictions = FC<GroupPredictionsProps> { props ->
    Grid {
        container = true
        spacing = 2.asDynamic()
        columns = 2.asDynamic()

        props.predictions.map { (question, histogram) ->
            Grid {
                item = true
                xs = true
                PredictionPlot {
                    this.question = question
                    this.histogram = histogram
                    key = question.id
                }
            }
        }
    }
}