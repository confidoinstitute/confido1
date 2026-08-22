package tools.confido.web.components.redesign.questions.predictions

import tools.confido.web.components.redesign.basic.*
import web.cssom.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
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
import tools.confido.rooms.*
import tools.confido.spaces.*
import tools.confido.web.components.useMyUser
import tools.confido.web.components.redesign.forms.ButtonUnstyled
import tools.confido.web.components.redesign.rooms.RoomContext
import tools.confido.web.components.redesign.questions.dialog.TimelineMarkDialog
import tools.confido.state.Topics
import tools.confido.utils.formatPercent
import tools.confido.utils.unixNow
import tools.confido.web.hooks.useSubscription
import web.dom.document
import web.events.EventType
import web.events.addEventListener
import web.events.removeEventListener
import web.html.HTMLDivElement
import web.keyboard.KeyboardEvent
import kotlin.math.max

/**
 * A moment on the timeline: the raw instant [ts] the user points at, plus the
 * update whose belief was held then ([point], the newest update at or before
 * [ts]). Display uses the instant; ghost fetching/caching uses the update.
 */
class TimelineMoment(val ts: Int, val point: GroupPredHistoryPoint)

external interface GroupPredictionTimelineProps : PropsWithElementSize {
    var question: Question
    var resolution: Value?
    /** Called with the hovered moment, null on leave. Drives the scrub ghost. */
    var onHover: ((TimelineMoment?) -> Unit)?
    /** Pin request: a moment to pin, or null to unpin (click on/near the pin line).
     *  Parent owns the pin state. */
    var onPin: ((TimelineMoment?) -> Unit)?
    /** Instant of the pinned moment, marked with a dashed line where clicked. */
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
    val room = use(RoomContext)
    // Unconditional hook call (hook order must not depend on the nullable context).
    val myUser = useMyUser()
    val canManageMarks = room?.hasPermission(myUser, RoomPermission.MANAGE_QUESTIONS) ?: false
    var markDialogOpen by useState(false)

    // Escape: close the marker dialog if open, else release the pin. Lives here
    // (not in QuestionPage) so an open dialog can't lose the pin underneath it.
    useEffectWithCleanup {
        val onKey: (KeyboardEvent) -> Unit = { e ->
            if (e.key == "Escape") {
                if (markDialogOpen) markDialogOpen = false
                else props.onPin?.invoke(null)
            }
        }
        document.addEventListener(EventType<KeyboardEvent>("keydown"), onKey)
        onCleanup { document.removeEventListener(EventType<KeyboardEvent>("keydown"), onKey) }
    }

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
            // Quiet separation from the distribution graph above.
            marginTop = 6.px
            borderTop = Border(0.5.px, LineStyle.solid, Color("#E5E5E5"))
            paddingTop = 8.px
        }
        fun momentAt(offsetX: Double): TimelineMoment? {
            val frac = ((offsetX - SIDE_PAD) / (width - 2 * SIDE_PAD)).coerceIn(0.0, 1.0)
            val ts = t0 + (frac * (tEnd - t0)).toInt()
            return points.lastOrNull { it.ts <= ts }?.let { TimelineMoment(ts, it) }
        }
        onMouseMove = { ev ->
            val rect = (ev.currentTarget as HTMLDivElement).getBoundingClientRect()
            val moment = momentAt(ev.clientX - rect.left)
            hoverTs = moment?.ts
            props.onHover?.invoke(moment)
        }
        onMouseLeave = { hoverTs = null; props.onHover?.invoke(null) }
        props.onPin?.let { pin ->
            // Recomputed from the event coordinates rather than read from hover
            // state — the click may land before the hover re-render commits.
            onClick = { ev ->
                val rect = (ev.currentTarget as HTMLDivElement).getBoundingClientRect()
                val offsetX = ev.clientX - rect.left
                val nearPin = props.pinnedTs?.let { kotlin.math.abs(offsetX - tx(it)) < 6.0 } ?: false
                if (nearPin) pin(null)
                else momentAt(offsetX)?.let(pin)
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
                display = Display.flex
                justifyContent = JustifyContent.spaceBetween
            }
            div {
                css { display = Display.flex; gap = 8.px; alignItems = AlignItems.baseline }
                span {
                    css {
                        textTransform = TextTransform.uppercase
                        fontSize = 10.px
                        letterSpacing = 0.8.px
                        fontWeight = integer(600)
                        color = Color("rgba(0,0,0,45%)")
                    }
                    +"History"
                }
                span { +"${history!!.updates} updates" }
            }
            if (canManageMarks && room != null) {
                // Styled as a small button so it reads as actionable, not as a label.
                ButtonUnstyled {
                    css {
                        fontSize = 11.px
                        lineHeight = 12.px
                        color = Color("#6319FF")
                        fontFamily = sansSerif
                        fontWeight = integer(600)
                        border = Border(1.px, LineStyle.solid, Color("#D5C8F3"))
                        borderRadius = 4.px
                        padding = Padding(1.px, 7.px)
                        backgroundColor = Color("#F6F2FD")
                        hover {
                            backgroundColor = Color("#E8E0F5")
                        }
                    }
                    +"+ marker"
                    onClick = { it.stopPropagation(); markDialogOpen = true }
                }
            }
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

            // Timeline markers: named moments, question-scoped and room-wide merged.
            // Labels stack greedily into rows so clustered marks stay readable.
            val marks = ((room?.timelineMarks ?: emptyList()) + props.question.timelineMarks)
                .filter { it.ts in t0..tEnd }
            val labelRowEnds = mutableListOf<Double>()
            marks.map { mark ->
                val x = tx(mark.ts)
                val flip = x > width - 90
                val estWidth = 4.0 + mark.name.length * 5.0
                val startX = if (flip) x - 4 - estWidth else x + 4
                Triple(mark, x, flip) to startX
            }.sortedBy { it.second }.forEach { (info, startX) ->
                val (mark, x, flip) = info
                var row = labelRowEnds.indexOfFirst { it <= startX }
                if (row == -1) { labelRowEnds.add(0.0); row = labelRowEnds.size - 1 }
                labelRowEnds[row] = startX + 4.0 + mark.name.length * 5.0 + 6.0
                line {
                    x1 = x; x2 = x
                    y1 = TOP_PAD; y2 = TOP_PAD + GRAPH_HEIGHT
                    stroke = "rgba(0,0,0,25%)"
                    strokeWidth = 1.0
                    strokeDasharray = "2 3"
                }
                text {
                    this.x = if (flip) x - 4 else x + 4
                    y = TOP_PAD + 8.0 + row * 11.0
                    textAnchor = if (flip) TextAnchor.end else TextAnchor.start
                    fontSize = 9.0
                    fill = "rgba(0,0,0,45%)"
                    +mark.name
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

    // Sibling of the handler div, so dialog clicks cannot bubble into pin toggling.
    if (room != null) {
        TimelineMarkDialog {
            open = markDialogOpen
            onClose = { markDialogOpen = false }
            this.room = room
            this.question = props.question
            // A pinned moment is the natural default for a new marker.
            defaultTime = props.pinnedTs?.let { Instant.fromEpochSeconds(it.toLong()) }
        }
    }
})
