package tools.confido.web.components.redesign.questions.predictions

import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.line
import react.dom.svg.ReactSVG.svg
import react.dom.svg.ReactSVG.text
import react.dom.svg.StrokeLinecap
import react.dom.svg.TextAnchor
import tools.confido.distributions.BinaryDistribution
import tools.confido.payloads.responses.UserPredCompareRow
import tools.confido.utils.List2
import tools.confido.utils.formatPercent
import tools.confido.web.components.redesign.basic.*
import web.cssom.*
import web.html.HTMLElement
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Individual-movement views of the compare feature (binary questions): how each
 * predictor moved between the two compared moments. Ported from the
 * update_scatter_plot extension, compacted for inline question-page use and
 * fixed to the two-timepoint case.
 */

external interface CompareUsersProps : PropsWithElementSize {
    var rows: List<UserPredCompareRow>
    /** Column labels for the two compared moments. */
    var labels: List2<String>
    /** Statically label each predictor's newest point with their name (presenter
     *  opt-in; the question page uses hover tooltips instead). */
    var showNames: Boolean?
}

/** Green for upward updates, red for downward, gray near zero (nonlinear ramp). */
private fun updateColor(delta: Double): String {
    val t = abs(delta).pow(0.4).coerceIn(0.0, 1.0)
    val hue = if (delta >= 0) 130 else 0
    return "hsl($hue, ${(t * 70).toInt()}%, ${(65 - t * 20).toInt()}%)"
}

private fun UserPredCompareRow.probs(): List2<Double?> =
    preds.map { (it?.dist as? BinaryDistribution)?.yesProb }

/** Deterministic small offset so coinciding predictors stay distinguishable. */
private fun jitter(rowIndex: Int, col: Int): Double =
    (((rowIndex * 31 + col * 17) % 100) - 50) / 50.0 * 8.0

private data class CompareHover(val rowIndices: List<Int>, val x: Double, val y: Double)

private fun segmentDistance(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
    val dx = x2 - x1; val dy = y2 - y1
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0.0) return sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
    val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0.0, 1.0)
    val nx = x1 + t * dx; val ny = y1 + t * dy
    return sqrt((px - nx) * (px - nx) + (py - ny) * (py - ny))
}

/** Shared hover tooltip: one line per hit predictor with its movement. */
private fun ChildrenBuilder.hoverTooltip(hover: CompareHover, rows: List<UserPredCompareRow>, chartW: Double, chartH: Double) {
    val shown = if (hover.rowIndices.size > 6) hover.rowIndices.take(5) else hover.rowIndices
    div {
        css {
            position = Position.absolute
            if (hover.x < chartW / 2) left = (hover.x / chartW * 100).pct else right = (100 - hover.x / chartW * 100).pct
            top = (hover.y / chartH * 100).coerceIn(5.0, 80.0).pct
            transform = translatey((-50).pct)
            backgroundColor = Color("rgba(255,255,255,0.94)")
            border = Border(0.5.px, LineStyle.solid, Color("#CCCCCC"))
            borderRadius = 5.px
            padding = Padding(3.px, 8.px)
            fontSize = 11.px
            lineHeight = 15.px
            whiteSpace = WhiteSpace.nowrap
            pointerEvents = None.none
            zIndex = integer(5)
        }
        shown.forEach { i ->
            val (p1, p2) = rows[i].probs()
            div {
                css { display = Display.flex; alignItems = AlignItems.center; gap = 5.px }
                span {
                    css {
                        display = Display.inlineBlock
                        width = 7.px; height = 7.px
                        borderRadius = 50.pct
                        backgroundColor = Color(updateColor((p2 ?: p1 ?: 0.0) - (p1 ?: p2 ?: 0.0)))
                        flexShrink = number(0.0)
                    }
                }
                rows[i].user?.displayName?.let { name ->
                    span { css { fontWeight = integer(600); color = Color("#555555") }; +name }
                }
                span {
                    css { color = Color("#666666") }
                    +"${p1?.let { formatPercent(it) } ?: "—"} → ${p2?.let { formatPercent(it) } ?: "—"}"
                }
            }
        }
        if (hover.rowIndices.size > shown.size) {
            div {
                css { color = Color("#999999"); fontStyle = FontStyle.italic }
                +"…and ${hover.rowIndices.size - shown.size} more"
            }
        }
    }
}

private const val SLOPE_HEIGHT = 260.0

/** Slope ("changes") chart: one line per predictor from the first moment to the second. */
val CompareChangesChart = elementSizeWrapper(FC<CompareUsersProps>("CompareChangesChart") { props ->
    var hover by useState<CompareHover?>(null)
    val width = props.elementWidth
    val height = SLOPE_HEIGHT
    val padTop = 30.0; val padBottom = 14.0
    val padSide = 90.0.coerceAtMost(width / 5)

    fun py(v: Double) = padTop + (height - padTop - padBottom) * (1 - v)
    fun cx(col: Int) = if (col == 0) padSide else width - padSide

    data class Seg(val row: Int, val x1: Double, val y1: Double, val x2: Double, val y2: Double)
    val segs = useMemo(props.rows, width) {
        props.rows.mapIndexedNotNull { i, row ->
            val (p1, p2) = row.probs()
            if (p1 == null || p2 == null) null
            else Seg(i, cx(0) + jitter(i, 0), py(p1), cx(1) + jitter(i, 1), py(p2))
        }
    }

    val active = hover?.rowIndices?.toSet() ?: emptySet()

    div {
        css { position = Position.relative }
        onMouseMove = { ev ->
            val el = ev.currentTarget as HTMLElement
            val rect = el.getBoundingClientRect()
            val mx = ev.clientX - rect.left
            val my = ev.clientY - rect.top
            val hits = segs.filter { segmentDistance(mx, my, it.x1, it.y1, it.x2, it.y2) <= 10.0 }.map { it.row }
            hover = if (hits.isEmpty()) null else CompareHover(hits, mx, my)
        }
        onMouseLeave = { hover = null }

        svg {
            this.width = width
            this.height = height
            viewBox = "0 0 $width $height"
            css { pointerEvents = None.none; display = Display.block }

            // Probability gridlines with the frame at 0/100%.
            listOf(0.0, 0.25, 0.5, 0.75, 1.0).forEach { v ->
                line {
                    x1 = padSide - 14; x2 = width - padSide + 14
                    y1 = py(v); y2 = py(v)
                    stroke = if (v == 0.0 || v == 1.0) "rgba(0,0,0,15%)" else "rgba(0,0,0,8%)"
                    strokeWidth = 1.0
                    if (v != 0.0 && v != 1.0) strokeDasharray = "4 4"
                }
                text {
                    x = padSide - 18; y = py(v) + 3.0
                    textAnchor = TextAnchor.end
                    fontSize = 9.0
                    fill = when (v) {
                        1.0 -> "#00CC2E"
                        0.0 -> "#FF5555"
                        else -> "rgba(0,0,0,30%)"
                    }
                    +when (v) {
                        1.0 -> "Yes"
                        0.0 -> "No"
                        else -> formatPercent(v, decimals = 0)
                    }
                }
            }
            // Column labels: the compared moments.
            List2(0, 1).forEach { col ->
                text {
                    x = cx(col); y = padTop - 12.0
                    textAnchor = TextAnchor.middle
                    fontSize = 10.0
                    fontStyle = "italic"
                    fill = "rgba(0,0,0,45%)"
                    +props.labels[col]
                }
            }

            segs.forEach { seg ->
                val (p1, p2) = props.rows[seg.row].probs()
                val isActive = hover == null || seg.row in active
                line {
                    x1 = seg.x1; y1 = seg.y1; x2 = seg.x2; y2 = seg.y2
                    stroke = updateColor((p2 ?: 0.0) - (p1 ?: 0.0))
                    strokeWidth = 5.0
                    strokeOpacity = if (isActive) (if (hover == null) "0.35" else "0.85") else "0.06"
                    strokeLinecap = StrokeLinecap.round
                }
            }
            // Dots on top of lines (also for one-sided rows).
            props.rows.forEachIndexed { i, row ->
                val probs = row.probs()
                val isActive = hover == null || i in active
                List2(0, 1).forEach { col ->
                    probs[col]?.let { v ->
                        circle {
                            this.cx = cx(col) + jitter(i, col); cy = py(v)
                            r = if (i in active) 4.0 else 3.0
                            fill = "#333333"
                            fillOpacity = if (isActive) "0.9" else "0.1"
                        }
                    }
                }
            }
            if (props.showNames == true) {
                props.rows.forEachIndexed { i, row ->
                    val name = row.user?.displayName ?: return@forEachIndexed
                    val probs = row.probs()
                    val col = if (probs.e2 != null) 1 else 0
                    val v = probs[col] ?: return@forEachIndexed
                    text {
                        x = cx(col) + jitter(i, col) + 8.0
                        y = py(v) + 3.0
                        textAnchor = TextAnchor.start
                        fontSize = 9.0
                        fill = "rgba(0,0,0,60%)"
                        +name
                    }
                }
            }
        }

        hover?.let { hoverTooltip(it, props.rows, width, height) }
    }
})

private const val SCATTER_HEIGHT = 280.0

/** Scatter: first moment on x, second on y; the diagonal is "no change". */
val CompareScatterChart = elementSizeWrapper(FC<CompareUsersProps>("CompareScatterChart") { props ->
    var hover by useState<CompareHover?>(null)
    val width = props.elementWidth
    val height = SCATTER_HEIGHT
    val padLeft = 40.0; val padRight = 12.0; val padTop = 16.0; val padBottom = 30.0
    val plotW = width - padLeft - padRight
    val plotH = height - padTop - padBottom

    fun sx(v: Double) = padLeft + v * plotW
    fun sy(v: Double) = padTop + (1 - v) * plotH

    data class Dot(val row: Int, val x: Double, val y: Double, val p1: Double, val p2: Double)
    val dots = useMemo(props.rows, width) {
        props.rows.mapIndexedNotNull { i, row ->
            val (p1, p2) = row.probs()
            if (p1 == null || p2 == null) null
            else Dot(i, sx(p1) + jitter(i, 0) / 2, sy(p2) + jitter(i, 1) / 2, p1, p2)
        }
    }

    div {
        css { position = Position.relative }
        onMouseMove = { ev ->
            val el = ev.currentTarget as HTMLElement
            val rect = el.getBoundingClientRect()
            val mx = ev.clientX - rect.left
            val my = ev.clientY - rect.top
            val hits = dots.filter { sqrt((it.x - mx) * (it.x - mx) + (it.y - my) * (it.y - my)) <= 10.0 }.map { it.row }
            hover = if (hits.isEmpty()) null else CompareHover(hits, mx, my)
        }
        onMouseLeave = { hover = null }

        svg {
            this.width = width
            this.height = height
            viewBox = "0 0 $width $height"
            css { pointerEvents = None.none; display = Display.block }

            listOf(0.0, 0.25, 0.5, 0.75, 1.0).forEach { v ->
                line {
                    x1 = sx(v); x2 = sx(v); y1 = padTop; y2 = padTop + plotH
                    stroke = if (v == 0.0 || v == 1.0) "rgba(0,0,0,15%)" else "rgba(0,0,0,7%)"
                    strokeWidth = 1.0
                }
                line {
                    x1 = padLeft; x2 = padLeft + plotW; y1 = sy(v); y2 = sy(v)
                    stroke = if (v == 0.0 || v == 1.0) "rgba(0,0,0,15%)" else "rgba(0,0,0,7%)"
                    strokeWidth = 1.0
                }
                text {
                    x = sx(v); y = height - padBottom + 12.0
                    textAnchor = TextAnchor.middle
                    fontSize = 9.0; fill = "rgba(0,0,0,30%)"
                    +formatPercent(v, decimals = 0)
                }
                text {
                    x = padLeft - 5.0; y = sy(v) + 3.0
                    textAnchor = TextAnchor.end
                    fontSize = 9.0; fill = "rgba(0,0,0,30%)"
                    +formatPercent(v, decimals = 0)
                }
            }
            // No-change diagonal: above it moved up, below moved down.
            line {
                x1 = sx(0.0); y1 = sy(0.0); x2 = sx(1.0); y2 = sy(1.0)
                stroke = "rgba(0,0,0,25%)"
                strokeWidth = 1.0
                strokeDasharray = "4 3"
            }
            // Axis titles: the compared moments.
            text {
                x = padLeft + plotW / 2; y = height - 4.0
                textAnchor = TextAnchor.middle
                fontSize = 10.0; fontStyle = "italic"; fill = "rgba(0,0,0,45%)"
                +props.labels.e1
            }
            text {
                x = 10.0; y = padTop + plotH / 2
                textAnchor = TextAnchor.middle
                fontSize = 10.0; fontStyle = "italic"; fill = "rgba(0,0,0,45%)"
                transform = "rotate(-90 10 ${padTop + plotH / 2})"
                +props.labels.e2
            }

            dots.forEach { dot ->
                val isActive = hover == null || dot.row in (hover?.rowIndices ?: emptyList())
                circle {
                    cx = dot.x; cy = dot.y
                    r = if (isActive && hover != null) 6.0 else 5.0
                    fill = updateColor(dot.p2 - dot.p1)
                    fillOpacity = if (isActive) "0.85" else "0.15"
                    stroke = "#FFFFFF"
                    strokeWidth = 1.0
                }
            }
            if (props.showNames == true) {
                dots.forEach { dot ->
                    props.rows[dot.row].user?.displayName?.let { name ->
                        text {
                            x = dot.x + 8.0
                            y = dot.y + 3.0
                            textAnchor = TextAnchor.start
                            fontSize = 9.0
                            fill = "rgba(0,0,0,60%)"
                            +name
                        }
                    }
                }
            }
        }

        hover?.let { hoverTooltip(it, props.rows, width, height) }
    }
})
