package components.presenter

import tools.confido.question.BinaryHistogram
import components.DistributionPlot
import components.redesign.basic.Stack
import components.redesign.basic.sansSerif
import components.redesign.questions.predictions.BinaryPrediction
import emotion.react.css
import hooks.useSubscription
import tools.confido.payloads.responses.WSData
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import tools.confido.distributions.BinaryDistribution
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.BinaryValue
import tools.confido.state.MultiQuestionPV
import tools.confido.utils.formatPercent
import web.cssom.*
import kotlin.math.roundToInt
import tools.confido.state.Topics
import tools.confido.refs.ref
import components.QuestionPredictors
import components.useQuestion
import tools.confido.refs.Ref

val MultiQuestionPP = FC<PresenterPageProps<MultiQuestionPV>> { props ->

    div {
        css {
            width = 100.vw
            height = 100.vh
            padding = 2.vh
            boxSizing = BoxSizing.borderBox
            display = Display.grid
            this.asDynamic().gridTemplateColumns = "repeat(auto-fit, minmax(400px, 1fr))"
            gap = 2.vh
            alignContent = AlignContent.center
        }
        props.view.questions.forEach { question ->
            MultiQuestionTile {
                key = question.id
                this.question = question
                this.showGroupPred = props.view.showGroupPred
                this.showHistogram = props.view.showHistogram
                this.showResolution = props.view.showResolution
            }
        }
    }
}

private external interface MultiQuestionTileProps : Props {
    var question: Ref<Question>
    var showGroupPred: Boolean
    var showHistogram: Boolean
    var showResolution: Boolean
}

private val MultiQuestionTile = FC<MultiQuestionTileProps> { props ->
    // Component-per-tile so each tile owns its question subscription (hook-safe).
    val question = useQuestion(props.question) ?: return@FC
    val isBinary = question.answerSpace is BinarySpace

    Stack {
        css {
            border = Border(1.px, LineStyle.solid, Color("#e0e0e0"))
            borderRadius = 1.vh
            padding = 2.vh
            overflow = Overflow.hidden
            gap = 1.5.vh
            minHeight = 0.px
        }
        // Question title
        div {
            css {
                fontFamily = sansSerif
                textAlign = TextAlign.center
                fontWeight = integer(600)
                fontSize = 3.5.vh
                lineHeight = 1.2.em
            }
            +question.name
        }
        // Group prediction
        if (props.showGroupPred) {
            TileGroupPred {
                this.question = question
                this.showResolution = props.showResolution
            }
        }
        // Histogram (binary only)
        if (props.showHistogram && isBinary) {
            TileHistogram {
                this.question = question
            }
        }
        // Footer
        div {
            css {
                fontFamily = sansSerif
                fontSize = 2.vh
                color = Color("#666")
                textAlign = TextAlign.center
            }
            QuestionPredictors { this.question = question.ref }
            +" people answered"
        }
    }
}

private external interface TileGroupPredProps : Props {
    var question: Question
    var showResolution: Boolean
}

private val TileGroupPred = FC<TileGroupPredProps> { props ->
    val question = props.question
    val response = useSubscription(Topics.groupPred(question.ref))

    div {
        css {
            width = 100.pct
            flexGrow = number(1.0)
            minHeight = 0.px
        }
        if (response is WSData) {
            response.data?.dist?.let { dist ->
                if (dist is BinaryDistribution) {
                    BinaryPrediction {
                        baseHeight = 20.vh
                        this.dist = dist
                        isGroup = true
                        interactive = false
                        if (props.showResolution && question.resolution != null)
                            resolution = question.resolution as? BinaryValue?
                    }
                } else {
                    DistributionPlot {
                        distribution = dist
                        fontSize = 16.0
                        if (props.showResolution)
                            resolutionLine = question.resolution?.value as? Double
                    }
                }
            } ?: div {
                css {
                    fontFamily = sansSerif
                    fontSize = 2.5.vh
                    textAlign = TextAlign.center
                    color = Color("#999")
                }
                +"No predictions yet"
            }
        }
    }
}

private val BAR_COLOR = Color("#8BF08E")

private external interface TileHistogramProps : Props {
    var question: Question
}

private val TileHistogram = FC<TileHistogramProps> { props ->
    val question = props.question
    val response = useSubscription(Topics.histogram(question.ref))

    if (response !is WSData) return@FC
    val histogram = response.data
    val maxCount = histogram.bins.maxOfOrNull { it.count } ?: 0
    if (maxCount == 0) return@FC

    val maxBarVh = 15.0
    val unitVh = minOf(maxBarVh / maxCount, 3.0)

    div {
        css {
            width = 100.pct
            flexGrow = number(1.0)
            display = Display.flex
            flexDirection = FlexDirection.row
            alignItems = AlignItems.flexEnd
            gap = 0.4.vw
            fontFamily = sansSerif
            minHeight = 0.px
        }
        histogram.bins.forEach { bin ->
            Stack {
                css {
                    flexGrow = number(1.0)
                    flexBasis = 0.px
                    minWidth = 0.px
                    justifyContent = JustifyContent.flexEnd
                    alignItems = AlignItems.stretch
                    gap = 0.3.vh
                }
                if (bin.count > 0) {
                    div {
                        css {
                            textAlign = TextAlign.center
                            fontSize = 1.8.vh
                            fontWeight = integer(600)
                        }
                        +"${bin.count}"
                    }
                    div {
                        css {
                            height = (bin.count * unitVh).vh
                            backgroundColor = BAR_COLOR
                            borderRadius = 0.3.vh
                        }
                    }
                }
                div {
                    css {
                        textAlign = TextAlign.center
                        fontSize = 1.3.vh
                        color = Color("#666666")
                        whiteSpace = WhiteSpace.nowrap
                    }
                    +"${(bin.min * 100).roundToInt()}–${(bin.max * 100).roundToInt()}%"
                }
            }
        }
    }
    // Median / mean summary
    Stack {
        direction = FlexDirection.row
        css {
            justifyContent = JustifyContent.center
            fontFamily = sansSerif
            fontSize = 1.6.vh
            color = Color("#666")
            gap = 1.vh
        }
        listOfNotNull(
            histogram.median?.let { "median ${formatPercent(it)}" },
            histogram.mean?.let { "mean ${formatPercent(it)}" },
        ).joinToString(" · ").let { if (it.isNotEmpty()) div { +it } }
    }
}
