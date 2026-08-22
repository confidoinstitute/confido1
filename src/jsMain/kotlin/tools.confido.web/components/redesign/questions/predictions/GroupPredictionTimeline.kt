package tools.confido.web.components.redesign.questions.predictions

import tools.confido.web.components.redesign.basic.*
import web.cssom.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.svg.ReactSVG.line
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.dom.svg.ReactSVG.text
import react.dom.svg.TextAnchor
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tools.confido.payloads.responses.GroupPredHistoryPoint
import tools.confido.payloads.responses.WSData
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.spaces.*
import tools.confido.state.Topics
import tools.confido.utils.formatPercent
import tools.confido.utils.unixNow
import tools.confido.web.hooks.useSubscription
import web.html.HTMLDivElement
import kotlin.math.max

external interface GroupPredictionTimelineProps : PropsWithElementSize {
    var question: Question
    var resolution: Value?
    /** Called with the update the cursor is over (step semantics: the newest update
     *  at or before the hovered time), null on leave. Drives the scrub ghost. */
    var onHoverPoint: ((GroupPredHistoryPoint?) -> Unit)?
    /** Click handler for pinning the hovered update; parent owns the toggle state. */
    var onTogglePin: ((GroupPredHistoryPoint) -> Unit)?
    /** Timestamp of the pinned update, marked with a dashed line. */
    var pinnedTs: Int?
}

private const val GRAPH_HEIGHT = 110.0
private const val TOP_PAD = 8.0
private const val RUG_HEIGHT = 8.0
private const val DATE_LABELS_HEIGHT = 20.0

fun formatUpdateDate(ts: Int): String {
    val d = Instant.fromEpochSeconds(ts.toLong()).toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$month ${d.dayOfMonth}, ${d.year}"
}

/** Vertices of a step-after polyline through [pts], extended horizontally to [endX]. */
private fun stepVertices(pts: List<Pair<Double, Double>>, endX: Double): List<Pair<Double, Double>> {
    val out = mutableListOf<Pair<Double, Double>>()
    pts.forEach { (x, y) ->
        if (out.isNotEmpty()) out += x to out.last().second
        out += x to y
    }
    out += endX to out.last().second
    return out
}

private fun pathD(vertices: List<Pair<Double, Double>>, close: Boolean = false) =
    vertices.mapIndexed { i, (x, y) -> "${if (i == 0) "M" else "L"} $x $y" }
        .joinToString(" ") + if (close) " Z" else ""

/**
 * The group prediction history timeline: a step chart of the group aggregate over
 * time (beliefs are held until updated, so segments extend to the next update).
 * Binary questions plot the yes-probability; numeric questions plot the median
 * with the 80% confidence band in the app-wide band colour. Renders nothing until
 * the history has at least two updates.
 */
val GroupPredictionTimeline = elementSizeWrapper(FC<GroupPredictionTimelineProps>("GroupPredictionTimeline") { props ->
    val history = (useSubscription(Topics.groupPredHistory(props.question.ref)) as? WSData)?.data
    var hoverTs by useState<Int?>(null)

    val points = history?.points ?: emptyList()
    if (points.size < 2) return@FC

    val space = props.question.answerSpace
    val width = props.elementWidth
    val svgHeight = TOP_PAD + GRAPH_HEIGHT + RUG_HEIGHT

    val t0 = points.first().ts
    val tEnd = max(points.last().ts, unixNow()).let { if (it == t0) t0 + 1 else it }
    fun tx(ts: Int) = SIDE_PAD + (ts - t0).toDouble() / (tEnd - t0) * (width - 2 * SIDE_PAD)

    val (yMin, yMax) = when (space) {
        is NumericSpace -> space.min to space.max
        else -> 0.0 to 1.0
    }
    fun ty(v: Double) = TOP_PAD + GRAPH_HEIGHT - (v.coerceIn(yMin, yMax) - yMin) / (yMax - yMin) * GRAPH_HEIGHT
    fun formatY(v: Double) = when (space) {
        is BinarySpace -> formatPercent(v)
        is NumericSpace -> space.formatValue(v, false, true)
        else -> v.toString()
    }

    val hasBand = space is NumericSpace && points.all { it.lo != null && it.hi != null }
    val hoverPoint = hoverTs?.let { ts -> points.lastOrNull { it.ts <= ts } }

    val gridValues = when (space) {
        is NumericSpace -> markSpacing(GRAPH_HEIGHT * 1.6, space.min, space.max) { space.formatValue(it, false, true) }
        else -> listOf(0.0, 0.5, 1.0)
    }

    div {
        css {
            position = Position.relative
            fontFamily = sansSerif
        }
        onMouseMove = { ev ->
            val rect = (ev.currentTarget as HTMLDivElement).getBoundingClientRect()
            val x = ev.clientX - rect.left
            val frac = ((x - SIDE_PAD) / (width - 2 * SIDE_PAD)).coerceIn(0.0, 1.0)
            val ts = t0 + (frac * (tEnd - t0)).toInt()
            hoverTs = ts
            props.onHoverPoint?.invoke(points.lastOrNull { it.ts <= ts })
        }
        onMouseLeave = { hoverTs = null; props.onHoverPoint?.invoke(null) }
        props.onTogglePin?.let { togglePin ->
            // Recomputed from the event coordinates rather than read from hover
            // state — the click may land before the hover re-render commits.
            onClick = { ev ->
                val rect = (ev.currentTarget as HTMLDivElement).getBoundingClientRect()
                val frac = ((ev.clientX - rect.left - SIDE_PAD) / (width - 2 * SIDE_PAD)).coerceIn(0.0, 1.0)
                val ts = t0 + (frac * (tEnd - t0)).toInt()
                points.lastOrNull { it.ts <= ts }?.let(togglePin)
            }
        }

        // Idle summary row; hover details ride on the crosshair tooltip below.
        div {
            css {
                height = 16.px
                fontSize = 12.px
                lineHeight = 16.px
                padding = Padding(0.px, SIDE_PAD.px)
                color = Color("rgba(0,0,0,30%)")
            }
            +"${history!!.updates} updates"
        }

        svg {
            this.width = width
            this.height = svgHeight
            viewBox = "0 0 $width $svgHeight"

            // Gridlines with value labels, recessive.
            gridValues.forEach { v ->
                line {
                    x1 = SIDE_PAD; x2 = width - SIDE_PAD
                    y1 = ty(v); y2 = ty(v)
                    stroke = "rgba(0,0,0,8%)"
                    strokeWidth = 1.0
                }
                text {
                    x = SIDE_PAD - 4.0
                    y = ty(v) + 3.0
                    textAnchor = TextAnchor.end
                    fontSize = 9.0
                    fill = "rgba(0,0,0,30%)"
                    // Grid labels get whole percents; the hover readout keeps precision.
                    +if (space is BinarySpace) formatPercent(v, decimals = 0) else formatY(v)
                }
            }

            // 80% confidence band (numeric questions).
            if (hasBand) {
                val hiV = stepVertices(points.map { tx(it.ts) to ty(it.hi!!) }, tx(tEnd))
                val loV = stepVertices(points.map { tx(it.ts) to ty(it.lo!!) }, tx(tEnd))
                path {
                    d = pathD(hiV + loV.reversed(), close = true)
                    fill = "#7ff7ff"
                    fillOpacity = "0.45"
                    stroke = "none"
                }
            }

            // Resolution reference line (same green as the "correct answer" flag).
            props.resolution?.let { resolution ->
                val v = when (resolution) {
                    is NumericValue -> resolution.value
                    is BinaryValue -> if (resolution.value) 1.0 else 0.0
                    else -> null
                }
                v?.let {
                    line {
                        x1 = SIDE_PAD; x2 = width - SIDE_PAD
                        y1 = ty(it); y2 = ty(it)
                        stroke = "#00CC2E"
                        strokeWidth = 1.5
                        strokeDasharray = "4 3"
                    }
                }
            }

            // The group aggregate as a step line: a belief holds until it is updated.
            // Center-orange to match the "group estimate" flag and slider center.
            path {
                d = pathD(stepVertices(points.map { tx(it.ts) to ty(it.mid) }, tx(tEnd)))
                fill = "none"
                stroke = "#FF8A00"
                strokeWidth = 2.0
            }

            // Event rug: one tick per update, so burstiness stays visible even
            // where the line is flat.
            points.forEach { p ->
                line {
                    x1 = tx(p.ts); x2 = tx(p.ts)
                    y1 = TOP_PAD + GRAPH_HEIGHT + 2.0; y2 = TOP_PAD + GRAPH_HEIGHT + RUG_HEIGHT
                    stroke = "rgba(0,0,0,25%)"
                    strokeWidth = 1.5
                }
            }

            // Pinned-update marker.
            props.pinnedTs?.let { ts ->
                line {
                    x1 = tx(ts); x2 = tx(ts)
                    y1 = TOP_PAD; y2 = TOP_PAD + GRAPH_HEIGHT
                    stroke = "rgba(0,0,0,45%)"
                    strokeWidth = 1.0
                    strokeDasharray = "3 3"
                }
            }

            // Hover crosshair.
            hoverTs?.let { ts ->
                line {
                    x1 = tx(ts); x2 = tx(ts)
                    y1 = TOP_PAD; y2 = TOP_PAD + GRAPH_HEIGHT
                    stroke = "rgba(0,0,0,20%)"
                    strokeWidth = 1.0
                }
            }
        }

        // Crosshair tooltip: date + value-as-of-then, flipped to whichever side of
        // the cursor has room. pointer-events off so it never eats the mousemove.
        hoverTs?.let { ts ->
            val hx = tx(ts)
            val onLeftHalf = hx < width / 2
            div {
                css {
                    position = Position.absolute
                    top = (16 + TOP_PAD + 2).px
                    left = if (onLeftHalf) (hx + 8).px else (hx - 8).px
                    if (!onLeftHalf) transform = translatex((-100).pct)
                    pointerEvents = None.none
                    backgroundColor = Color("rgba(255,255,255,0.92)")
                    border = Border(0.5.px, LineStyle.solid, Color("#CCCCCC"))
                    borderRadius = 5.px
                    padding = Padding(3.px, 7.px)
                    fontSize = 11.px
                    lineHeight = 14.px
                    whiteSpace = WhiteSpace.nowrap
                }
                div { css { color = Color("rgba(0,0,0,45%)") }; +formatUpdateDate(ts) }
                hoverPoint?.let { p ->
                    div {
                        css { fontWeight = integer(600); color = Color("#555555") }
                        +if (hasBand) "${formatY(p.mid)} (${formatY(p.lo!!)} – ${formatY(p.hi!!)})"
                         else formatY(p.mid)
                    }
                }
            }
        }

        // Date labels for the x range (the rug carries the in-between structure).
        div {
            css {
                height = DATE_LABELS_HEIGHT.px
                padding = Padding(0.px, SIDE_PAD.px)
                display = Display.flex
                justifyContent = JustifyContent.spaceBetween
                fontSize = 12.px
                color = Color("rgba(0,0,0,30%)")
            }
            div { +formatUpdateDate(t0) }
            div { +formatUpdateDate(tEnd) }
        }
    }
})
