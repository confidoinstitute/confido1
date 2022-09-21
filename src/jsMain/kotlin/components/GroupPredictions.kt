package components

import csstype.px
import emotion.react.css
import mui.material.*
import react.*
import space.kscience.plotly.layout
import space.kscience.plotly.models.Bar
import space.kscience.plotly.models.Pie
import tools.confido.question.*
import tools.confido.utils.binBorders
import tools.confido.utils.binRanges
import utils.jsObject
import kotlin.js.Date

external interface PredictionPlotProps : Props {
    var question: Question
    var histogram: List<Double>?
}

val PredictionPlot = FC<PredictionPlotProps> {props ->
    val config = jsObject {
        this.responsive = true
    }
    val histogram = props.histogram ?: List(props.question.answerSpace.bins) { 0.0 }

    Card {
        raised = true


        CardHeader {
            title = ReactNode(props.question.name)
        }
        CardContent {
            css {
                height = 500.px
                minWidth = 500.px
            }
            when(val answerSpace = props.question.answerSpace) {
                is BinaryAnswerSpace -> ReactPlotly {
                    if (histogram.size != 2)
                        error("This is not a correct size")
                    traces = listOf(
                        Pie {
                            labels(listOf("No", "Yes"))
                            values(histogram)
                            sort = false
                        }
                    )
                    this.config = config
                }
                is NumericAnswerSpace -> ReactPlotly {
                    traces = listOf(
                        Bar {
                            val xBins = binRanges(answerSpace.min, answerSpace.max, answerSpace.bins).map { (it.second + it.first) / 2 }
                            if (answerSpace.representsDays) {
                                x.set(xBins.map { Date(it * 1000).toISOString() })
                            } else {
                                x.set(xBins)
                            }
                            y.set(histogram)
                            hoverinfo = "x"
                        }
                    )
                    plotlyInit = { plot ->
                        plot.layout {
                            bargap = 0
                            yaxis {
                                visible = false
                                showline = false
                            }
                        }
                    }

                    this.config = config
                }
            }
        }
    }
}

external interface GroupPredictionsProps : Props {
    var questions: List<Question>?
}

val GroupPredictions = FC<GroupPredictionsProps> { props ->
    val appState = useContext(AppStateContext)
    val questions = props.questions ?: appState.questions.values.filter { it.visible && it.predictionsVisible }.sortedBy { it.name }
    Grid {
        container = true
        spacing = 2.asDynamic()
        columns = 2.asDynamic()

        questions.map { question ->
            Grid {
                item = true
                xs = true
                PredictionPlot {
                    appState.groupDistributions[question.id]?.let {
                        this.histogram = it
                    }
                    this.question = question
                    key = question.id
                }
            }
        }
    }
}