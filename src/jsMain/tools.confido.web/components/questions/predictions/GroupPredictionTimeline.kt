package tools.confido.web.components.questions.predictions

import tools.confido.web.hooks.useDocumentEventListener
import tools.confido.web.components.basic.*
import web.cssom.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.line
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.rect
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
import tools.confido.utils.List2
import tools.confido.web.components.useMyUser
import tools.confido.web.components.PresenterIcon
import tools.confido.web.components.forms.ButtonUnstyled
import tools.confido.web.components.presenter.PresenterContext
import tools.confido.state.TimelinePV
import tools.confido.web.components.layout.LayoutMode
import tools.confido.web.components.layout.LayoutModeContext
import tools.confido.web.components.rooms.RoomContext
import tools.confido.web.components.questions.dialog.TimelineMarkDialog
import tools.confido.api.Api
import tools.confido.state.Topics
import tools.confido.utils.formatPercent
import tools.confido.utils.unixNow
import tools.confido.web.Client
import tools.confido.web.utils.runCoroutine
import tools.confido.web.hooks.useSubscription
import tools.confido.web.hooks.usePureClick
import tools.confido.web.hooks.combineRefs
import tools.confido.web.utils.panzoom1d.PZDragMode
import tools.confido.web.utils.panzoom1d.PZParams
import tools.confido.web.utils.panzoom1d.PZState
import tools.confido.web.utils.panzoom1d.usePanZoom
import web.events.EventType
import web.html.HTMLDivElement
import web.html.HTMLElement
import web.keyboard.KeyboardEvent
import kotlin.math.abs
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
    /** Armed one-shot capture: the next click reports via [onPickMoment] instead
     *  of pinning (the compare view's "pick on the timeline…" eyedropper). */
    var pickArmed: Boolean?
    var onPickMoment: ((TimelineMoment) -> Unit)?
    /** Escape pressed while armed: cancel the capture without picking. */
    var onPickCancel: (() -> Unit)?
    /** Non-interactive presenter rendering: no header controls, gestures, hover
     *  or pin; the window is fixed to [windowStart]/[windowEnd] (null = full).
     *  The presenter page scales the whole chart via CSS zoom. */
    var presenter: Boolean?
    var windowStart: Int?
    var windowEnd: Int?
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

fun formatUpdateDateTime(ts: Int): String {
    val dt = Instant.fromEpochSeconds(ts.toLong()).toLocalDateTime(TimeZone.currentSystemDefault())
    return formatUpdateDate(ts) +
        " ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
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

private val ZOOM_PRESETS = listOf("3m" to 90 * 86400, "1m" to 30 * 86400, "1w" to 7 * 86400, "1d" to 86400)

private val presenterIconSize = emotion.css.ClassName { width = 14.px; height = 14.px }

/**
 * The group prediction history timeline: a step chart of the group aggregate over
 * time (beliefs are held until updated, so segments extend to the next update).
 * Binary questions plot the yes-probability; numeric questions plot the median
 * with the 80% confidence band in the app-wide band colour. Renders nothing until
 * the history has at least two updates.
 *
 * Zoom model (time axes get range semantics, value axes keep pan/zoom):
 * mouse drag selects a range to zoom to (Grafana-style), wheel and pinch zoom
 * around the cursor, single-finger touch pans, and quick-range chips jump to
 * the last day/week/month ("All" resets). Click still pins; a drag past the
 * small movement threshold never pins.
 */
val GroupPredictionTimeline = elementSizeWrapper(FC<GroupPredictionTimelineProps>("GroupPredictionTimeline") { props ->
    val history = (useSubscription(Topics.groupPredHistory(props.question.ref)) as? WSData)?.data
    var hoverTs by useState<Int?>(null)
    val room = use(RoomContext)
    val presenterCtl = use(PresenterContext)
    // Unconditional hook call (hook order must not depend on the nullable context).
    val myUser = useMyUser()
    val layoutMode = use(LayoutModeContext)!!
    val canManageMarks = room?.hasPermission(myUser, RoomPermission.MANAGE_QUESTIONS) ?: false
    var markDialogOpen by useState(false)
    var markersMenuOpen by useState(false)

    val points = history?.points ?: emptyList()
    val width = props.elementWidth
    val t0 = points.firstOrNull()?.ts ?: 0
    val tEnd = max(points.lastOrNull()?.ts ?: 1, unixNow()).let { if (it == t0) t0 + 1 else it }

    // Time-axis pan/zoom (time axes get range-select for mouse drags; touch pans,
    // pinch and wheel zoom, horizontal scroll pans). Allows zooming to ~1 hour.
    val presenter = props.presenter == true
    val zoomParams = PZParams(contentDomain = t0.toDouble()..tEnd.toDouble(), viewportWidth = width,
        sidePad = SIDE_PAD, maxZoom = ((tEnd - t0) / 3600.0).coerceAtLeast(1.0))
    val (chartRef, liveZoomState, ctl) = usePanZoom<HTMLDivElement>(zoomParams)
    // Presenter windows show a fixed window instead of the interactive zoom state.
    val zoomState = if (presenter) {
        val ws = (props.windowStart?.toDouble() ?: zoomParams.contentDomain.start)
            .coerceIn(zoomParams.contentDomain)
        val we = (props.windowEnd?.toDouble() ?: zoomParams.contentDomain.endInclusive)
            .coerceIn(zoomParams.contentDomain)
        if (we > ws && (ws > zoomParams.contentDomain.start || we < zoomParams.contentDomain.endInclusive))
            zoomParams.solveMapMap(List2(ws, we), List2(SIDE_PAD, width - SIDE_PAD))
        else PZState(zoomParams)
    } else liveZoomState
    var selRange by useState<ClosedFloatingPointRange<Double>?>(null)
    ctl.dragMode = { PZDragMode.SELECT }
    ctl.onSelectionChange = { selRange = it }

    fun momentAt(offsetX: Double): TimelineMoment? {
        val ts = zoomState.viewportToContent(offsetX).toInt()
        return points.lastOrNull { it.ts <= ts }?.let { TimelineMoment(ts, it) }
    }
    // Pin on a "pure" click (no drag, no hold) — drags are selections and never
    // pin; usePureClick's movement threshold is the disambiguation. An armed
    // eyedropper captures the moment instead and leaves the pin alone.
    val pinClickRef = usePureClick<HTMLDivElement> { ev ->
        val el = ev.currentTarget as HTMLElement
        val offsetX = ev.clientX - el.getBoundingClientRect().left
        if (props.pickArmed == true) {
            momentAt(offsetX)?.let { props.onPickMoment?.invoke(it) }
        } else props.onPin?.let { pin ->
            val nearPin = props.pinnedTs?.let {
                abs(offsetX - zoomState.contentToViewport(it.toDouble())) < 6.0 } ?: false
            if (nearPin) pin(null)
            else momentAt(offsetX)?.let(pin)
        }
    }
    val containerRef = combineRefs<HTMLDivElement>(chartRef, pinClickRef)

    // Escape: close the marker dialog if open, else release the pin. Lives here
    // (not in QuestionPage) so an open dialog can't lose the pin underneath it.
    useDocumentEventListener(EventType<KeyboardEvent>("keydown")) { e ->
        if (e.key == "Escape") {
            when {
                markDialogOpen -> markDialogOpen = false
                markersMenuOpen -> markersMenuOpen = false
                props.pickArmed == true -> props.onPickCancel?.invoke()
                else -> props.onPin?.invoke(null)
            }
        }
    }

    if (points.size < 2) return@FC

    val space = props.question.answerSpace
    val svgHeight = TOP_PAD + GRAPH_HEIGHT + RUG_HEIGHT

    fun tx(ts: Int) = zoomState.contentToViewport(ts.toDouble())
    val visRange = zoomState.visibleContentRange
    val visSpan = visRange.endInclusive - visRange.start
    // The step series needs the last point before the window (its belief enters
    // from the left edge); contentToViewport clamps it to the edge.
    val visPoints = listOfNotNull(points.lastOrNull { it.ts < visRange.start }) +
            points.filter { it.ts.toDouble() in visRange }

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
    fun formatEdge(tsD: Double): String {
        val ts = tsD.toInt()
        return if (visSpan < 3 * 86400) {
            val dt = Instant.fromEpochSeconds(ts.toLong()).toLocalDateTime(TimeZone.currentSystemDefault())
            formatUpdateDate(ts) +
                " ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
        } else formatUpdateDate(ts)
    }

    val hasBand = space is NumericSpace && points.all { it.lo != null && it.hi != null }
    val hoverPoint = hoverTs?.let { ts -> points.lastOrNull { it.ts <= ts } }
    val allMarks = ((room?.timelineMarks ?: emptyList()) + props.question.timelineMarks).sortedBy { it.ts }

    val gridValues = when (space) {
        is NumericSpace -> markSpacing(GRAPH_HEIGHT * 1.6, space.min, space.max) { space.formatValue(it, false, true) }
        else -> listOf(0.0, 0.5, 1.0)
    }

    fun zoomToLast(secs: Int) {
        val p = ctl.params
        val end = p.contentDomain.endInclusive
        ctl.state = p.solveMapMap(
            List2(maxOf(p.contentDomain.start, end - secs), end),
            List2(p.sidePad, p.viewportWidth - p.sidePad))
    }

    // Selecting a marker in the menu: bring it into view (zoomed-out reset when
    // it is outside the window) and pin its moment — on desktop the pin opens
    // the compare-vs-now view with the marker's named chip.
    fun jumpToMark(mark: TimelineMark) {
        if (mark.ts.toDouble() !in visRange) ctl.state = PZState(ctl.params)
        points.lastOrNull { it.ts <= mark.ts }?.let { props.onPin?.invoke(TimelineMoment(mark.ts, it)) }
    }

    div {
        css {
            fontFamily = sansSerif
            // Quiet separation from the distribution graph above.
            marginTop = 6.px
            borderTop = Border(0.5.px, LineStyle.solid, Color("#E5E5E5"))
            paddingTop = 8.px
        }

        // Idle summary row; hover details ride on the crosshair tooltip below.
        if (!presenter) div {
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
            div {
                css { display = Display.flex; gap = 10.px; alignItems = AlignItems.center }
                // Quick ranges, anchored to "now"; "All" doubles as zoom reset.
                val fullSpan = tEnd - t0
                val presets = ZOOM_PRESETS.filter { it.second < fullSpan }
                if (presets.isNotEmpty()) {
                    div {
                        css { display = Display.flex; gap = 2.px; alignItems = AlignItems.center }
                        (listOf("All" to null) + presets).forEach { (label, secs) ->
                            val active = if (secs == null) zoomState.zoom < 1.001
                                else abs(visSpan - secs) < secs * 0.05 &&
                                    abs(visRange.endInclusive - tEnd) < secs * 0.05
                            ButtonUnstyled {
                                css {
                                    fontSize = 10.px
                                    lineHeight = 12.px
                                    fontFamily = sansSerif
                                    padding = Padding(1.px, 5.px)
                                    borderRadius = 3.px
                                    if (active) {
                                        color = Color("#6319FF")
                                        fontWeight = integer(700)
                                        backgroundColor = Color("#F6F2FD")
                                    } else {
                                        color = Color("rgba(0,0,0,40%)")
                                        hover { backgroundColor = Color("#00000010") }
                                    }
                                }
                                +label
                                onClick = { ev ->
                                    ev.stopPropagation()
                                    if (secs == null) ctl.state = PZState(ctl.params) else zoomToLast(secs)
                                }
                            }
                        }
                    }
                }
                // Send this timeline (with its current window, if zoomed) to the
                // presenter screen.
                if (presenterCtl != null && layoutMode >= LayoutMode.TABLET) {
                    ButtonUnstyled {
                        css {
                            color = Color("rgba(0,0,0,40%)")
                            display = Display.flex
                            alignItems = AlignItems.center
                            padding = Padding(0.px, 2.px)
                            hover { color = Color("#6319FF") }
                        }
                        PresenterIcon { className = presenterIconSize }
                        onClick = { ev ->
                            ev.stopPropagation()
                            val zoomed = zoomState.zoom > 1.001
                            presenterCtl.offer(TimelinePV(props.question.ref,
                                windowStart = if (zoomed) visRange.start.toInt() else null,
                                windowEnd = if (zoomed) visRange.endInclusive.toInt() else null))
                        }
                    }
                }
                // Markers: one affordance for both navigation and management. With
                // markers present, everyone gets the "Markers…" menu — selecting a
                // marker jumps it into view and pins it (⇒ compare-vs-now on
                // desktop); managers also get inline delete and the add dialog.
                // With none, managers see the direct "+ marker" entry.
                if (room != null && (allMarks.isNotEmpty() || canManageMarks)) {
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
                        +if (allMarks.isEmpty()) "+ marker" else "Markers…"
                        onClick = { it.stopPropagation()
                            if (allMarks.isEmpty()) markDialogOpen = true else markersMenuOpen = true }
                    }
                    DialogMenu {
                        open = markersMenuOpen
                        onClose = { markersMenuOpen = false }
                        DialogMenuHeader { text = "Timeline markers" }
                        allMarks.forEach { mark ->
                            val isRoomWide = room.timelineMarks.any { it.id == mark.id }
                            Stack {
                                direction = FlexDirection.row
                                css {
                                    alignItems = AlignItems.center
                                    padding = Padding(0.px, 15.px)
                                    hover { backgroundColor = rgb(0, 0, 0, 0.05) }
                                }
                                ButtonUnstyled {
                                    css {
                                        flexGrow = number(1.0)
                                        textAlign = TextAlign.left
                                        padding = Padding(6.px, 0.px)
                                        fontFamily = sansSerif
                                    }
                                    div { css { fontSize = 15.px; lineHeight = 19.px }; +mark.name }
                                    div {
                                        css { fontSize = 11.px; lineHeight = 14.px; color = Color("#999999") }
                                        +(formatUpdateDateTime(mark.ts) + if (isRoomWide) " · whole room" else "")
                                    }
                                    onClick = { markersMenuOpen = false; jumpToMark(mark) }
                                }
                                if (canManageMarks) {
                                    ButtonUnstyled {
                                        css {
                                            color = Color("#999999")
                                            fontSize = 14.px
                                            padding = Padding(4.px, 0.px, 4.px, 10.px)
                                            hover { color = Color("#555555") }
                                        }
                                        +"✕"
                                        onClick = {
                                            runCoroutine {
                                                if (isRoomWide) Client.call(Api.roomTimelineMarkDelete(room.ref, mark.id))
                                                else Client.call(Api.questionTimelineMarkDelete(props.question.ref, mark.id))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (canManageMarks) {
                            DialogMenuSeparator {}
                            DialogMenuItem {
                                text = "Add marker…"
                                onClick = { markDialogOpen = true }
                            }
                        }
                    }
                }
            }
        }

        // Gesture surface: only the chart area carries pin/select/zoom/hover, so
        // header buttons never double as chart clicks (native listeners on an
        // ancestor are not stopped by React's stopPropagation).
        div {
        if (!presenter) {
            ref = containerRef
            onMouseMove = { ev ->
                if (selRange != null) {
                    // Selecting a range; the crosshair/tooltip would just be noise.
                    hoverTs = null
                    props.onHover?.invoke(null)
                } else {
                    val rectBox = (ev.currentTarget as HTMLDivElement).getBoundingClientRect()
                    val moment = momentAt(ev.clientX - rectBox.left)
                    hoverTs = moment?.ts
                    props.onHover?.invoke(moment)
                }
            }
            onMouseLeave = { hoverTs = null; props.onHover?.invoke(null) }
        }
        css {
            position = Position.relative
            touchAction = None.none
            // A range-select drag must not double as native text selection.
            userSelect = None.none
            if (props.pickArmed == true) cursor = Cursor.crosshair
        }

        svg {
            this.width = width
            this.height = svgHeight
            viewBox = "0 0 $width $svgHeight"
            // Purely presentational: keep the container the single pointer target,
            // so gestures (usePan capture, usePureClick's pointer-out abort) never
            // depend on which thin line or path happens to sit under the cursor.
            css { pointerEvents = None.none }

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
            if (hasBand && visPoints.isNotEmpty()) {
                val hiV = stepVertices(visPoints.map { tx(it.ts) to ty(it.hi!!) }, tx(tEnd))
                val loV = stepVertices(visPoints.map { tx(it.ts) to ty(it.lo!!) }, tx(tEnd))
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
            val marks = allMarks.filter { it.ts.toDouble() in visRange }
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
            if (visPoints.isNotEmpty()) {
                path {
                    d = pathD(stepVertices(visPoints.map { tx(it.ts) to ty(it.mid) }, tx(tEnd)))
                    fill = "none"
                    stroke = "#FF8A00"
                    strokeWidth = 2.0
                }
            }

            // Event rug: one tick per update, so burstiness stays visible even
            // where the line is flat.
            points.filter { it.ts.toDouble() in visRange }.forEach { p ->
                line {
                    x1 = tx(p.ts); x2 = tx(p.ts)
                    y1 = TOP_PAD + GRAPH_HEIGHT + 2.0; y2 = TOP_PAD + GRAPH_HEIGHT + RUG_HEIGHT
                    stroke = "rgba(0,0,0,25%)"
                    strokeWidth = 1.5
                }
            }

            // Pinned-moment marker (hidden when panned/zoomed out of view).
            props.pinnedTs?.takeIf { it.toDouble() in visRange }?.let { ts ->
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

            // Live range-selection highlight (selection itself lives in PZController).
            selRange?.let { sel ->
                rect {
                    x = sel.start
                    y = TOP_PAD
                    this.width = sel.endInclusive - sel.start
                    this.height = GRAPH_HEIGHT
                    fill = "rgba(0,194,255,0.10)"
                    stroke = "rgba(0,194,255,0.5)"
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
                    top = (TOP_PAD + 2).px
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
                div { css { color = Color("rgba(0,0,0,45%)") }; +formatEdge(ts.toDouble()) }
                hoverPoint?.let { p ->
                    div {
                        css { fontWeight = integer(600); color = Color("#555555") }
                        +if (hasBand) "${formatY(p.mid)} (${formatY(p.lo!!)} – ${formatY(p.hi!!)})"
                         else formatY(p.mid)
                    }
                }
                // Contextual teaching in place of a dedicated Compare button.
                val hint = when {
                    props.pickArmed == true -> "click to set the compare point"
                    props.pinnedTs == null && props.onPin != null &&
                        layoutMode >= LayoutMode.TABLET -> "click to compare with now"
                    else -> null
                }
                hint?.let {
                    div {
                        css { color = Color("rgba(0,0,0,30%)"); fontSize = 10.px; fontStyle = FontStyle.italic }
                        +it
                    }
                }
            }
        }

        // Date labels for the visible range (the rug carries the in-between structure).
        div {
            css {
                height = DATE_LABELS_HEIGHT.px
                padding = Padding(0.px, SIDE_PAD.px)
                display = Display.flex
                justifyContent = JustifyContent.spaceBetween
                fontSize = 12.px
                color = Color("rgba(0,0,0,30%)")
            }
            div { +formatEdge(visRange.start) }
            div { +formatEdge(visRange.endInclusive) }
        }
        } // gesture surface
    }

    // Sibling of the handler div, so dialog clicks cannot bubble into pin toggling.
    // Not mounted in presenter windows: Dialog asserts contexts (SidebarContext)
    // that only the main app layout provides.
    if (room != null && !presenter) {
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
