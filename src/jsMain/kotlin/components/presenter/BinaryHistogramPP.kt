package components.presenter

import BinaryHistogram
import HistogramBin
import components.redesign.basic.Stack
import components.redesign.basic.sansSerif
import web.cssom.*
import emotion.react.css
import hooks.useSubscription
import js.objects.unsafeJso as jso
import payloads.responses.WSData
import react.FC
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.state.BinaryHistogramPV
import tools.confido.utils.formatPercent
import kotlin.math.roundToInt
import tools.confido.state.Topics
import tools.confido.refs.ref
import components.QuestionPredictors
import components.useQuestion

private val BAR_COLOR = Color("#8BF08E")

private fun binLabel(bin: HistogramBin) =
    "${(bin.min * 100).roundToInt()}–${(bin.max * 100).roundToInt()}%"

val BinaryHistogramPP = FC<PresenterPageProps<BinaryHistogramPV>> { props ->
    // Hooks precede the data-dependent early returns: on a cold load the
    // subscriptions deliver after the first render, and the hook order must
    // not change between the loading and loaded renders (React #310).
    val question = useQuestion(props.view.question)
    val response = useSubscription(question?.let { Topics.histogram(it.ref) })
    if (question == null) return@FC

    Stack {
        css {
            gap = 30.pt
            width = 100.vw
            height = 100.vh
            padding = 2.5.vh
            alignItems = AlignItems.center
            justifyContent = JustifyContent.spaceBetween
        }
        div {
            css {
                fontFamily = sansSerif
                textAlign = TextAlign.center
                fontWeight = integer(600)
                fontSize = 7.vh
                padding = 0.px
                margin = 0.px
            }
            +question.name
        }
        if (response is WSData) {
            val histogram = response.data
            val maxCount = histogram.bins.maxOfOrNull { it.count } ?: 0
            // One predictor = one unit of bar height, shared by both display modes, so that
            // toggling nicknames only subdivides the bars without changing their heights.
            val unitVh = if (maxCount > 0) minOf(55.0 / maxCount, 7.0) else 0.0
            if (maxCount == 0) {
                div {
                    css { fontFamily = sansSerif; fontSize = 6.vh; flexGrow = number(1.0) }
                    +"Nobody has yet answered."
                }
            } else {
                // Histogram: one column per bin, bars grow bottom-up.
                div {
                    css {
                        width = 100.pct
                        flexGrow = number(1.0)
                        display = Display.flex
                        flexDirection = FlexDirection.row
                        alignItems = AlignItems.stretch
                        gap = 0.6.vw
                        fontFamily = sansSerif
                    }
                    histogram.bins.forEach { bin ->
                        Stack {
                            css {
                                flexGrow = number(1.0)
                                flexBasis = 0.px
                                minWidth = 0.px
                                justifyContent = JustifyContent.flexEnd
                                alignItems = AlignItems.stretch
                                gap = 0.6.vh
                            }
                            if (bin.count > 0) div {
                                // Count above the bar
                                css {
                                    textAlign = TextAlign.center
                                    fontSize = 3.vh
                                    fontWeight = integer(600)
                                }
                                +"${bin.count}"
                            }
                            val users = bin.users
                            val chipFontVh = minOf(2.2, 0.75 * unitVh)
                            if (props.view.showNames && users != null && bin.count > 0) {
                                // The bar subdivided into one block per predictor.
                                div {
                                    css {
                                        display = Display.flex
                                        flexDirection = FlexDirection.column
                                        justifyContent = JustifyContent.flexEnd
                                        gap = 0.2.vh
                                    }
                                    users.forEach { up ->
                                        div {
                                            css {
                                                height = (unitVh - 0.2).vh
                                                lineHeight = (unitVh - 0.2).vh
                                                fontSize = chipFontVh.vh
                                                padding = Padding(0.vh, 0.6.vh)
                                                borderRadius = 0.4.vh
                                                backgroundColor = BAR_COLOR
                                                textAlign = TextAlign.center
                                                overflow = Overflow.hidden
                                                textOverflow = TextOverflow.ellipsis
                                                whiteSpace = WhiteSpace.nowrap
                                                if (up.isMyself) fontWeight = integer(600)
                                            }
                                            span {
                                                style = jso {
                                                    up.color?.let { color = Color(it) }
                                                }
                                                css { marginRight = 0.5.vh }
                                                +"•"
                                            }
                                            +(up.displayName ?: "(Anonymous)")
                                        }
                                    }
                                    if (bin.moreUsers > 0) div {
                                        // Overflow block sized as the users it stands for,
                                        // preserving the bar's total height.
                                        css {
                                            height = (bin.moreUsers * unitVh - 0.2).vh
                                            fontSize = chipFontVh.vh
                                            display = Display.flex
                                            alignItems = AlignItems.center
                                            justifyContent = JustifyContent.center
                                            borderRadius = 0.4.vh
                                            backgroundColor = BAR_COLOR
                                            fontStyle = FontStyle.italic
                                            overflow = Overflow.hidden
                                        }
                                        +"+${bin.moreUsers} more"
                                    }
                                }
                            } else if (bin.count > 0) {
                                div {
                                    // Solid bar, same per-person scale as the nickname blocks
                                    css {
                                        height = (bin.count * unitVh).vh
                                        backgroundColor = BAR_COLOR
                                        borderRadius = 0.4.vh
                                    }
                                }
                            }
                            div {
                                // Range label under the bar
                                css {
                                    textAlign = TextAlign.center
                                    fontSize = 2.2.vh
                                    color = Color("#666666")
                                    whiteSpace = WhiteSpace.nowrap
                                }
                                +binLabel(bin)
                            }
                        }
                    }
                }
            }
            Stack {
                direction = FlexDirection.row
                css {
                    width = 100.pct
                    justifyContent = JustifyContent.spaceBetween
                    fontFamily = sansSerif
                    fontSize = 4.vh
                }
                div {
                    QuestionPredictors { this.question = question.ref }
                    +" people answered"
                }
                div {
                    listOfNotNull(
                        histogram.median?.let { "median ${formatPercent(it)}" },
                        histogram.mean?.let { "mean ${formatPercent(it)}" },
                    ).joinToString(" · ").let { if (it.isNotEmpty()) +it }
                }
            }
        }
    }
}
