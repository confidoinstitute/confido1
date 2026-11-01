package tools.confido.web.components.questions.predictions

import emotion.react.*
import emotion.css.ClassName
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.line
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.rect
import react.dom.svg.ReactSVG.svg
import react.dom.svg.ReactSVG.text
import react.dom.svg.TextAnchor
import tools.confido.api.Api
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.payloads.responses.UserPredCompareRequest
import tools.confido.payloads.responses.UserPredCompareRow
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.spaces.*
import tools.confido.utils.List2
import tools.confido.utils.formatPercent
import tools.confido.web.ApiError
import tools.confido.web.Client
import tools.confido.web.components.PresenterIcon
import tools.confido.web.components.basic.*
import tools.confido.web.components.forms.ButtonUnstyled
import tools.confido.web.utils.runCoroutine
import tools.confido.web.utils.panzoom1d.PZParams
import tools.confido.web.hooks.*
import web.html.HTMLDivElement
import web.html.HTMLElement
import web.pointer.POINTER_LEAVE
import web.pointer.POINTER_MOVE
import web.pointer.PointerEvent
import web.cssom.*
import kotlin.math.max
import kotlin.math.min

/**
 * One side of a comparison. VALUE endpoints ([Now], [At]) name a fixed state;
 * [Selection] is a live *reference* to the timeline pin — it follows re-pins
 * and is exclusive to one side (there is one pin). Releasing a referenced pin
 * freezes the endpoint into [At] at its last value.
 */
sealed interface CompareEndpoint {
    /** The current group prediction (live). */
    object Now : CompareEndpoint
    /** Live-bound to the pinned timeline moment. */
    object Selection : CompareEndpoint
    /** A fixed instant. */
    data class At(val ts: Int) : CompareEndpoint
}

/** The two sides of an open comparison, older first (as the chart shows them),
 *  for text that accompanies the chart. A null dist is not (yet) available. */
data class CompareSides(
    val distA: ProbabilityDistribution?,
    val labelA: String,
    val distB: ProbabilityDistribution?,
    val labelB: String,
)

external interface CompareDistributionsProps : PropsWithElementSize {
    var space: Space
    var question: Question
    /** The older state. */
    var distA: ProbabilityDistribution?
    var labelA: String
    /** Resolved timestamp of the older state (null = now). */
    var tsA: Int?
    /** The newer state. */
    var distB: ProbabilityDistribution?
    var labelB: String
    /** Resolved timestamp of the newer state (null = now). */
    var tsB: Int?
    /** Offer the per-predictor tabs (changes/scatter/table). Binary questions
     *  only; the caller gates on VIEW_INDIVIDUAL_PREDICTIONS. */
    var individualTabs: Boolean?
    /** Chip click opens the endpoint picker for that side (0 = older, 1 = newer). */
    var onChipClick: ((Int) -> Unit)?
    /** Offer this view to the presenter screen; called with the active tab id
     *  ("dist" / "changes" / "scatter"). */
    var onPresent: ((String) -> Unit)?
    var onClose: (() -> Unit)?
    /** Show the terse delta lines under the chart. Defaults to true; turn off
     *  when a description renders the comparison in full sentences. */
    var showSummary: Boolean?
    /** Pan/zoom gestures and the zoom pill (numeric). Defaults to true. */
    var interactive: Boolean?
    /** Numeric view to start at / visible range while zoomed; see [useGraphZoom]. */
    var initialView: ClosedFloatingPointRange<Double>?
    var onViewChange: ((ClosedFloatingPointRange<Double>?) -> Unit)?
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
/** Text colors keyed to the two sides (stroke hues, darkened for legibility). */
val compareOldText = Color("#8a63d6")
val compareNewText = Color("#0090C8")
/** The hover tint over the region below the cursor (and its readout chip). */
private const val BELOW_TINT = "#E6E6E6"

private val presenterIconSize = ClassName { width = 14.px; height = 14.px }

private fun legendChip(bg: String, borderColor: String, clickable: Boolean = false) = ClassName {
    fontSize = 11.px
    lineHeight = 14.px
    fontWeight = integer(600)
    fontFamily = sansSerif
    color = Color("#555555")
    backgroundColor = Color(bg)
    border = Border(1.px, LineStyle.solid, Color(borderColor))
    borderRadius = 4.px
    padding = Padding(1.px, 7.px)
    whiteSpace = WhiteSpace.nowrap
    if (clickable) {
        cursor = Cursor.pointer
        hover { opacity = number(0.8) }
    }
}

/** A legend-style chip naming one side of a comparison (0 = older, 1 = newer). */
fun ChildrenBuilder.compareSideChip(side: Int, label: String) = span {
    className = if (side == 0) legendChip("#F3EDFC", DECREASE_FILL) else legendChip("#E9FBFF", NEW_STROKE)
    +label
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

    // Individual-movement tabs (binary + VIEW_INDIVIDUAL_PREDICTIONS only).
    val tabsAvailable = props.individualTabs == true && space is BinarySpace
    var tab by useState("dist")
    val activeTab = if (tabsAvailable) tab else "dist"
    var rows by useState<List<UserPredCompareRow>?>(null)
    val rowsReq = useRef("")
    // Per-predictor rows are fetched lazily, first time an individual tab opens;
    // distB doubles as the live-refresh trigger for a "now" endpoint (it changes
    // whenever a new group prediction lands).
    useEffect(props.tsA, props.tsB, activeTab == "dist", props.distB) {
        if (activeTab == "dist") return@useEffect
        val key = "${props.tsA}:${props.tsB}"
        rowsReq.current = key
        runCoroutine {
            val r = try {
                Client.call(Api.questionUserPredCompare(props.question.ref),
                    UserPredCompareRequest(props.tsA, props.tsB))
            } catch (e: ApiError) { null }
            if (rowsReq.current == key) rows = r
        }
    }

    // Value-axis zoom of the numeric chart. Hooks stay up here, unconditional (a
    // placeholder space stands in for binary questions, which never use it).
    val interactive = props.interactive ?: true
    val numeric = (space as? NumericSpace)?.let { sp ->
        (props.distA as? ContinuousProbabilityDistribution)?.let { a ->
            (props.distB as? ContinuousProbabilityDistribution)?.let { b -> Triple(sp, a, b) }
        }
    }
    val zoomSpace = space as? NumericSpace ?: NumericSpace(0.0, 1.0)
    val zoomParams = PZParams(contentDomain = zoomSpace.range, viewportWidth = width, sidePad = SIDE_PAD, maxZoom = 10.0)
    // Fitting both states, so neither curve gets cut off.
    val zoom = useGraphZoom(zoomParams, zoomSpace, numeric?.let { listOf(it.second, it.third) } ?: emptyList(),
        props.initialView, props.onViewChange, compactPill = true)
    val zs = zoom.state

    // Hover on the numeric chart: a cursor line, the below-cursor region tinted,
    // and P(answer < x) under both states. Native listeners: usePan's draggable
    // stops pointermove propagation, so React's delegated onPointerMove would not see it.
    var hoverPx by useState<Double>()
    val hoverMoveRE = useEventListener<HTMLDivElement, PointerEvent>(PointerEvent.POINTER_MOVE) { ev ->
        // Mouse only, and not mid-drag (a pan or range selection).
        hoverPx = if (ev.pointerType != "mouse" || ev.buttons.unsafeCast<Int>() != 0) null
            else ev.clientX - (ev.currentTarget as HTMLElement).getBoundingClientRect().left
    }
    val hoverLeaveRE = useEventListener<HTMLDivElement, PointerEvent>(PointerEvent.POINTER_LEAVE) { hoverPx = null }
    val chartRef = combineRefs(if (interactive) zoom.ref else null, hoverMoveRE, hoverLeaveRE)
    val contentViewport = zs.leftPadVisible..(zs.params.viewportWidth - zs.rightPadVisible)
    val cursorPx = hoverPx?.takeIf { activeTab == "dist" && it in contentViewport }
    val hoverSplit = cursorPx?.let { px -> numeric?.let { (sp, a, b) ->
        val unitsPerPx = zs.params.contentWidth / zs.paperWidth
        val x = zs.viewportToContent(px)
        GraphSplit(x, sp.formatAtResolution(x, unitsPerPx), below = b.cdf(x), belowBefore = a.cdf(x))
    } }
    // Mirrored to the description below the chart, when it listens.
    val splitChannel = use(GraphSplitContext)
    useEffect(hoverSplit) { splitChannel?.publish(hoverSplit) }
    useEffectWithCleanup(splitChannel) { onCleanup { splitChannel?.publish(null) } }

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
            // Chips double as endpoint pickers when the parent provides a handler.
            fun ChildrenBuilder.chip(side: Int, bg: String, borderColor: String, label: String) {
                props.onChipClick?.let { pick ->
                    ButtonUnstyled {
                        className = legendChip(bg, borderColor, clickable = true)
                        +"$label ▾"
                        onClick = { it.stopPropagation(); pick(side) }
                    }
                } ?: span { className = legendChip(bg, borderColor); +label }
            }
            chip(0, "#F3EDFC", DECREASE_FILL, props.labelA)
            span { css { color = Color("rgba(0,0,0,40%)"); fontSize = 12.px }; +"→" }
            chip(1, "#E9FBFF", NEW_STROKE, props.labelB)
            div { css { flexGrow = number(1.0) } }
            if (interactive && numeric != null && activeTab == "dist") +zoom.pill
            props.onPresent?.let { present ->
                ButtonUnstyled {
                    css {
                        color = Color("#999999")
                        display = Display.flex
                        alignItems = AlignItems.center
                        marginRight = 6.px
                        hover { color = Color("#6319FF") }
                    }
                    PresenterIcon { className = presenterIconSize }
                    onClick = { it.stopPropagation(); present(activeTab) }
                }
            }
            if (tabsAvailable) {
                div {
                    css { display = Display.flex; gap = 2.px; alignItems = AlignItems.center; marginRight = 8.px }
                    listOf("dist" to "Distributions", "changes" to "Changes",
                           "scatter" to "Scatter").forEach { (id, label) ->
                        ButtonUnstyled {
                            css {
                                fontSize = 10.px
                                lineHeight = 12.px
                                fontFamily = sansSerif
                                padding = Padding(1.px, 5.px)
                                borderRadius = 3.px
                                if (activeTab == id) {
                                    color = Color("#6319FF")
                                    fontWeight = integer(700)
                                    backgroundColor = Color("#F6F2FD")
                                } else {
                                    color = Color("rgba(0,0,0,40%)")
                                    hover { backgroundColor = Color("#00000010") }
                                }
                            }
                            +label
                            onClick = { it.stopPropagation(); tab = id }
                        }
                    }
                }
            }
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

        if (activeTab != "dist") {
            // Individual movements between the two moments.
            val r = rows
            when {
                r == null -> div {
                    css { padding = Padding(20.px, SIDE_PAD.px); color = Color("#999999"); fontSize = 13.px }
                    +"Loading…"
                }
                r.isEmpty() -> div {
                    css { padding = Padding(20.px, SIDE_PAD.px); color = Color("#999999"); fontSize = 13.px }
                    +"No individual ${props.question.predictionTerminology.plural} to show."
                }
                else -> {
                    val labels = List2(props.labelA, props.labelB)
                    when (activeTab) {
                        "changes" -> CompareChangesChart { this.rows = r; this.labels = labels }
                        "scatter" -> CompareScatterChart { this.rows = r; this.labels = labels }
                    }
                }
            }
        } else {
        val da = props.distA
        val db = props.distB
        when {
            space is NumericSpace && da is ContinuousProbabilityDistribution && db is ContinuousProbabilityDistribution -> {
                // Binned over the visible range only, so zooming in adds detail.
                val visible = zs.visibleDomainRange
                val bins = 240
                val binner = Binner(space.subspace(visible.start, visible.endInclusive), bins)
                val a = binner.binRanges.map { da.densityBetween(it) }
                val b = binner.binRanges.map { db.densityBetween(it) }
                // From the distributions, not the visible bins: panning must not rescale.
                val maxDensity = max(da.maxDensity, db.maxDensity).takeIf { it > 0.0 } ?: 1.0
                val yScale = CHART_HEIGHT / maxDensity
                fun px(i: Int) = zs.contentToViewport(binner.binMidpoints[i])
                fun py(v: Double) = CHART_TOP + CHART_HEIGHT - v * yScale
                fun areaPath(upper: List<Double>, lower: List<Double>): String {
                    val fwd = upper.mapIndexed { i, v -> "${px(i)} ${py(v)}" }
                    val back = lower.mapIndexed { i, v -> "${px(i)} ${py(v)}" }.reversed()
                    return "M " + (fwd + back).joinToString(" L ") + " Z"
                }
                fun linePath(vals: List<Double>) =
                    "M " + vals.mapIndexed { i, v -> "${px(i)} ${py(v)}" }.joinToString(" L ")
                val overlap = a.zip(b) { x, y -> min(x, y) }

                // Hover area: the chart plus its axis row.
                div {
                    ref = chartRef
                    css {
                        position = Position.relative
                        overflow = Overflow.hidden
                        // Vertical drags still scroll the page; pinch and sideways pans are ours.
                        touchAction = TouchAction.panY
                        graphZoomCursor(zoom)
                    }
                    zoom.selectionOverlay(0.0, CHART_TOP + CHART_HEIGHT)?.let { +it }
                    svg {
                        this.width = width
                        this.height = CHART_TOP + CHART_HEIGHT
                        viewBox = "0 0 $width ${CHART_TOP + CHART_HEIGHT}"
                        css { pointerEvents = None.none }
                        // Tint first, so the change fills keep their hues on top of it.
                        cursorPx?.let { px ->
                            rect {
                                x = zs.leftPadVisible; y = CHART_TOP
                                this.width = px - zs.leftPadVisible; this.height = CHART_HEIGHT
                                fill = BELOW_TINT; fillOpacity = "0.6"
                            }
                        }
                        path { d = areaPath(overlap, List(bins) { 0.0 }); fill = OVERLAP_FILL; stroke = "none" }
                        path { d = areaPath(a, overlap); fill = DECREASE_FILL; fillOpacity = "0.55"; stroke = "none" }
                        path { d = areaPath(b, overlap); fill = INCREASE_FILL; fillOpacity = "0.8"; stroke = "none" }
                        path { d = linePath(a); fill = "none"; stroke = OLD_STROKE; strokeWidth = 1.5; strokeDasharray = "5 3" }
                        path { d = linePath(b); fill = "none"; stroke = NEW_STROKE; strokeWidth = 2.0 }
                        cursorPx?.let { px ->
                            line {
                                x1 = px; x2 = px; y1 = 0.0; y2 = CHART_TOP + CHART_HEIGHT
                                stroke = "rgba(0,0,0,0.65)"; strokeWidth = 1.0
                            }
                        }
                    }
                    // Value axis labels.
                    div {
                        css {
                            position = Position.relative
                            height = AXIS_HEIGHT.px
                            fontSize = 12.px
                            color = Color("rgba(0,0,0,30%)")
                        }
                        // P(below) and P(above), older to newer, each on its side's own
                        // color (the tint for below, white-ish for the untinted rest).
                        val cursorReadout = if (cursorPx != null && hoverSplit != null) {
                            val before = hoverSplit.belowBefore ?: 0.0
                            // (Not `old`/`new`: `new` is reserved in JS and got mangled.)
                            fun chip(pBefore: Double, pAfter: Double, below: Boolean): ReadoutChip {
                                val (o, n) = formatPercent(pBefore) to formatPercent(pAfter)
                                val text = if (below) "← $o to $n" else "$o to $n →"
                                return ReadoutChip(text, Color(if (below) BELOW_TINT else "#F4F4F4")) {
                                    if (below) +"← "
                                    span { css { color = compareOldText }; +o }
                                    span { css { color = Color("#999999") }; +" to " }
                                    span { css { color = compareNewText }; +n }
                                    if (!below) +" →"
                                }
                            }
                            CursorReadout(cursorPx, width, hoverSplit.label,
                                below = chip(before, hoverSplit.below, below = true),
                                above = chip(1 - before, 1 - hoverSplit.below, below = false))
                        } else null
                        val marks = markSpacing(zs.paperWidth, space.min, space.max, space::formatMark)
                        marks.filter { it in visible }.forEach { v ->
                            val markPx = zs.contentToViewport(v)
                            val markLabel = space.formatMark(v, marks.step)
                            val markHalf = markLabel.length * 3.5
                            val covered = cursorReadout?.covered
                            span {
                                key = v.toString()
                                css {
                                    position = Position.absolute
                                    left = markPx.px
                                    transform = translatex((-50).pct)
                                    if (covered != null && markPx + markHalf > covered.start && markPx - markHalf < covered.endInclusive)
                                        visibility = Visibility.hidden
                                }
                                +markLabel
                            }
                        }
                        cursorReadout?.render(this)
                    }
                }
                // Delta summary.
                val ciA = da.confidenceInterval(0.8)
                val ciB = db.confidenceInterval(0.8)
                if (props.showSummary != false)
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
                // A shared grid label track keeps both bars starting at the same x
                // (equal probabilities must render as equal, aligned bars even when
                // one label is much longer than the other).
                div {
                    css {
                        padding = Padding(10.px, SIDE_PAD.px)
                        display = Display.grid
                        gridTemplateColumns = "fit-content(45%) 1fr max-content".unsafeCast<GridTemplateColumns>()
                        alignItems = AlignItems.center
                        columnGap = 10.px
                        rowGap = 8.px
                    }
                    listOf(Triple(props.labelA, da.yesProb, DECREASE_FILL), Triple(props.labelB, db.yesProb, INCREASE_FILL))
                        .forEach { (rowLabel, p, barFill) ->
                            span {
                                css { fontSize = 12.px; color = Color("#666666") }
                                +rowLabel
                            }
                            div {
                                css {
                                    height = 16.px
                                    backgroundColor = Color("#F0F0F0")
                                    borderRadius = 4.px
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
                                css { fontSize = 13.px; fontWeight = integer(600); color = Color("#555555") }
                                +formatPercent(p)
                            }
                        }
                    if (props.showSummary != false)
                    div {
                        css { fontSize = 12.px; color = Color("#666666"); gridColumn = "1 / -1".unsafeCast<GridColumn>() }
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
    }
})
