package tools.confido.web.components.redesign.questions.predictions

import tools.confido.web.components.redesign.basic.sansSerif
import web.cssom.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.distributions.QuantileSummaryDistribution
import tools.confido.spaces.NumericSpace
import tools.confido.spaces.NumericValue

external interface QuantileSummaryGraphProps : Props {
    var space: NumericSpace
    var dist: QuantileSummaryDistribution
    var resolution: NumericValue?
}

/**
 * Renderer for the quantile-only group aggregate of AGGREGATE_ONLY numeric
 * questions: nested interval band(s) + median marker on the answer axis. There is
 * deliberately no density curve to draw — the server never ships one for these
 * questions (its peaks would be the individual predictions).
 */
val QuantileSummaryGraph = FC<QuantileSummaryGraphProps> { props ->
    val space = props.space
    val dist = props.dist
    fun pct(v: Double) = ((v - space.min) / (space.max - space.min)).coerceIn(0.0, 1.0) * 100.0

    div {
        css {
            position = Position.relative
            height = 72.px
            fontFamily = sansSerif
        }
        // Axis line
        div {
            css {
                position = Position.absolute
                left = 0.pct; right = 0.pct; top = 44.px
                height = 2.px
                backgroundColor = Color("#DDDDDD")
            }
        }
        // Interval bands, widest (lowest p) drawn first so narrower ones nest on top
        dist.intervals.sortedBy { it.p }.reversed().forEachIndexed { i, ci ->
            div {
                key = "ci${ci.p}"
                css {
                    position = Position.absolute
                    left = pct(ci.start).pct
                    width = (pct(ci.end) - pct(ci.start)).pct
                    top = 36.px
                    height = 18.px
                    borderRadius = 4.px
                    backgroundColor = Color("#3055F1")
                    opacity = number(0.25 + 0.15 * i)
                }
                title = "${(ci.p * 100).toInt()}% of the group answer lies here"
            }
        }
        // Median marker + label
        div {
            css {
                position = Position.absolute
                left = pct(dist.median).pct
                top = 30.px
                height = 30.px
                width = 2.px
                marginLeft = (-1).px
                backgroundColor = Color("#3055F1")
            }
        }
        div {
            css {
                position = Position.absolute
                left = pct(dist.median).pct
                top = 8.px
                transform = translatex((-50).pct)
                fontSize = 14.px
                fontWeight = integer(600)
                color = Color("#3055F1")
                whiteSpace = WhiteSpace.nowrap
            }
            +space.formatValue(dist.median)
        }
        // Resolution marker (green), same vocabulary as the density graph
        props.resolution?.let { res ->
            div {
                css {
                    position = Position.absolute
                    left = pct(res.value).pct
                    top = 30.px
                    height = 30.px
                    width = 2.px
                    marginLeft = (-1).px
                    backgroundColor = Color("#00CC2E")
                }
                title = "Resolution: ${space.formatValue(res.value)}"
            }
        }
        // Axis end labels
        div {
            css {
                position = Position.absolute
                left = 0.pct; top = 52.px
                fontSize = 11.px; color = Color("#AAAAAA")
            }
            +space.formatValue(space.min)
        }
        div {
            css {
                position = Position.absolute
                right = 0.pct; top = 52.px
                fontSize = 11.px; color = Color("#AAAAAA")
            }
            +space.formatValue(space.max)
        }
    }
}
