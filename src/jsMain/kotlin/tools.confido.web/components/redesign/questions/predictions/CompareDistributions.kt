package tools.confido.web.components.redesign.questions.predictions

import emotion.react.*
import emotion.css.ClassName
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.dom.svg.ReactSVG.text
import react.dom.svg.TextAnchor
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.spaces.*
import tools.confido.utils.formatPercent
import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.forms.ButtonUnstyled
import web.cssom.*
import kotlin.math.max
import kotlin.math.min

external interface CompareDistributionsProps : PropsWithElementSize {
    var space: Space
    /** The older state. */
    var distA: ProbabilityDistribution?
    var labelA: String
    /** The newer state. */
    var distB: ProbabilityDistribution?
    var labelB: String
    var onClose: (() -> Unit)?
}

private const val CHART_HEIGHT = 150.0
private const val CHART_TOP = 6.0
private const val AXIS_HEIGHT = 22.0

/** Mass that arrived between the two states (new-only area). */
private const val INCREASE_FILL = "#7ff7ff"
/** Mass that departed (old-only area). */
private const val DECREASE_FILL = "#b08bff"
private const val OVERLAP_FILL = "#DDDDDD"
private const val OLD_STROKE = "#9d7bdd"
private const val NEW_STROKE = "#00C2FF"

private fun legendChip(bg: String, borderColor: String) = ClassName {
    fontSize = 11.px
    lineHeight = 14.px
    fontWeight = integer(600)
    color = Color("#555555")
    backgroundColor = Color(bg)
    border = Border(1.px, LineStyle.solid, Color(borderColor))
    borderRadius = 4.px
    padding = Padding(1.px, 7.px)
    whiteSpace = WhiteSpace.nowrap
}

/**
 * Symmetric comparison of two group distributions at different moments: both
 * states matter equally (unlike the scrub ghost's current-vs-outline), so the
 * plot colours *changes* — mass that departed (old-only, purple), mass that
 * arrived (new-only, cyan) and the unchanged overlap (grey) — with outline
 * strokes and dated legend chips carrying state identity. Binary questions get
 * a two-bar rendering; a delta summary line accompanies both.
 */
val CompareDistributions = elementSizeWrapper(FC<CompareDistributionsProps>("CompareDistributions") { props ->
    val width = props.elementWidth
    val space = props.space

    div {
        css {
            fontFamily = sansSerif
            padding = Padding(10.px, 0.px, 4.px, 0.px)
        }
        // Legend row: the two states, and a close button.
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                gap = 8.px
                padding = Padding(0.px, SIDE_PAD.px)
            }
            span { className = legendChip("#F3EDFC", DECREASE_FILL); +props.labelA }
            span { css { color = Color("rgba(0,0,0,40%)"); fontSize = 12.px }; +"→" }
            span { className = legendChip("#E9FBFF", NEW_STROKE); +props.labelB }
            div { css { flexGrow = number(1.0) } }
            props.onClose?.let { close ->
                ButtonUnstyled {
                    css {
                        fontSize = 14.px
                        color = Color("#999999")
                        hover { color = Color("#555555") }
                    }
                    +"✕"
                    onClick = { close() }
                }
            }
        }

        val da = props.distA
        val db = props.distB
        when {
            space is NumericSpace && da is ContinuousProbabilityDistribution && db is ContinuousProbabilityDistribution -> {
                val bins = 240
                val binner = Binner(space, bins)
                val a = binner.binRanges.map { da.densityBetween(it) }
                val b = binner.binRanges.map { db.densityBetween(it) }
                val maxDensity = max(a.max(), b.max()).takeIf { it > 0.0 } ?: 1.0
                val yScale = CHART_HEIGHT / maxDensity
                fun px(i: Int) = SIDE_PAD + (i + 0.5) / bins * (width - 2 * SIDE_PAD)
                fun py(v: Double) = CHART_TOP + CHART_HEIGHT - v * yScale
                fun areaPath(upper: List<Double>, lower: List<Double>): String {
                    val fwd = upper.mapIndexed { i, v -> "${px(i)} ${py(v)}" }
                    val back = lower.mapIndexed { i, v -> "${px(i)} ${py(v)}" }.reversed()
                    return "M " + (fwd + back).joinToString(" L ") + " Z"
                }
                fun linePath(vals: List<Double>) =
                    "M " + vals.mapIndexed { i, v -> "${px(i)} ${py(v)}" }.joinToString(" L ")
                val overlap = a.zip(b) { x, y -> min(x, y) }

                svg {
                    this.width = width
                    this.height = CHART_TOP + CHART_HEIGHT
                    viewBox = "0 0 $width ${CHART_TOP + CHART_HEIGHT}"
                    css { pointerEvents = None.none }
                    path { d = areaPath(overlap, List(bins) { 0.0 }); fill = OVERLAP_FILL; stroke = "none" }
                    path { d = areaPath(a, overlap); fill = DECREASE_FILL; fillOpacity = "0.55"; stroke = "none" }
                    path { d = areaPath(b, overlap); fill = INCREASE_FILL; fillOpacity = "0.8"; stroke = "none" }
                    path { d = linePath(a); fill = "none"; stroke = OLD_STROKE; strokeWidth = 1.5; strokeDasharray = "5 3" }
                    path { d = linePath(b); fill = "none"; stroke = NEW_STROKE; strokeWidth = 2.0 }
                }
                // Value axis labels.
                div {
                    css {
                        position = Position.relative
                        height = AXIS_HEIGHT.px
                        fontSize = 12.px
                        color = Color("rgba(0,0,0,30%)")
                    }
                    markSpacing(width - 2 * SIDE_PAD, space.min, space.max) { space.formatValue(it, false, true) }
                        .forEach { v ->
                            span {
                                css {
                                    position = Position.absolute
                                    left = (SIDE_PAD + (v - space.min) / (space.max - space.min) * (width - 2 * SIDE_PAD)).px
                                    transform = translatex((-50).pct)
                                }
                                +space.formatValue(v, false, true)
                            }
                        }
                }
                // Delta summary.
                val ciA = da.confidenceInterval(0.8)
                val ciB = db.confidenceInterval(0.8)
                div {
                    css {
                        padding = Padding(4.px, SIDE_PAD.px, 0.px, SIDE_PAD.px)
                        fontSize = 12.px
                        color = Color("#666666")
                    }
                    fun fmt(v: Double) = space.formatValue(v, false, true)
                    val dm = db.median - da.median
                    +"median ${fmt(da.median)} → ${fmt(db.median)} (${if (dm >= 0) "+" else ""}${fmt(dm)})"
                    +" · 80% CI width ${fmt(ciA.endInclusive - ciA.start)} → ${fmt(ciB.endInclusive - ciB.start)}"
                }
            }
            da is BinaryDistribution && db is BinaryDistribution -> {
                // Two probability bars; the delta is the whole story for binary.
                div {
                    css {
                        padding = Padding(10.px, SIDE_PAD.px)
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        gap = 8.px
                    }
                    listOf(Triple(props.labelA, da.yesProb, DECREASE_FILL), Triple(props.labelB, db.yesProb, INCREASE_FILL))
                        .forEach { (rowLabel, p, barFill) ->
                            div {
                                css { display = Display.flex; alignItems = AlignItems.center; gap = 10.px }
                                span {
                                    css { fontSize = 12.px; color = Color("#666666"); minWidth = 120.px }
                                    +rowLabel
                                }
                                div {
                                    css {
                                        flexGrow = number(1.0)
                                        height = 16.px
                                        backgroundColor = Color("#F0F0F0")
                                        borderRadius = 4.px
                                        position = Position.relative
                                    }
                                    div {
                                        css {
                                            this.width = (p * 100).pct
                                            height = 100.pct
                                            backgroundColor = Color(barFill)
                                            borderRadius = 4.px
                                        }
                                    }
                                }
                                span {
                                    css { fontSize = 13.px; fontWeight = integer(600); color = Color("#555555"); minWidth = 44.px }
                                    +formatPercent(p)
                                }
                            }
                        }
                    div {
                        css { fontSize = 12.px; color = Color("#666666") }
                        val d = (db.yesProb - da.yesProb) * 100
                        +"change: ${if (d >= 0) "+" else ""}${kotlin.math.round(d * 10) / 10} pp"
                    }
                }
            }
            else -> div {
                css { padding = Padding(20.px, SIDE_PAD.px); color = Color("#999999"); fontSize = 13.px }
                +"No group prediction is available for one of the compared moments."
            }
        }
    }
})
