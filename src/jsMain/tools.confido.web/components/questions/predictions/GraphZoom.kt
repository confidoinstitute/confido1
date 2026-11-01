package tools.confido.web.components.questions.predictions

import tools.confido.web.components.basic.MainPalette
import tools.confido.web.components.forms.ButtonUnstyled
import tools.confido.web.hooks.*
import web.cssom.*
import web.dom.Node
import web.events.EventType
import web.html.HTMLElement
import web.keyboard.KeyboardEvent
import web.pointer.PointerEvent
import csstype.PropertiesBuilder
import emotion.react.*
import js.objects.unsafeJso as jso
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.spaces.NumericSpace
import tools.confido.utils.List2
import tools.confido.web.utils.panzoom1d.*
import kotlin.math.abs
import kotlin.math.ln

/**
 * Zoom state and UI of a value-axis graph (see [useGraphZoom]). Attach [ref] to the
 * graph surface, render [pill] somewhere visible and [selectionOverlay] over the plot.
 */
class GraphZoom(
    val ref: RefObject<HTMLElement>,
    val state: PZState,
    val ctl: PZController,
    /** The loupe is armed: the next mouse drag selects a range to zoom to. */
    val armed: Boolean,
    val disarm: () -> Unit,
    val pill: ReactNode,
    private val selection: ClosedFloatingPointRange<Double>?,
) {
    /** Live drag-selection highlight, spanning [height] px from [top]. */
    fun selectionOverlay(top: Double, height: Double): ReactNode? = selection?.let { sel ->
        div.create {
            key = "selection"
            css {
                position = Position.absolute
                this.top = top.px
                this.height = height.px
                pointerEvents = None.none
                backgroundColor = Color("rgba(0,194,255,0.10)")
                border = Border(1.px, LineStyle.solid, Color("rgba(0,194,255,0.5)"))
                boxSizing = BoxSizing.borderBox
            }
            style = jso {
                left = sel.start.px
                width = (sel.endInclusive - sel.start).px
            }
        }
    }
}

/** Crosshair (and no text selection) while [zoom] is armed. */
fun PropertiesBuilder.graphZoomCursor(zoom: GraphZoom) {
    if (zoom.armed) {
        cursor = Cursor.crosshair
        // A range-select drag must not double as native text selection.
        userSelect = None.none
    }
}

/**
 * Pan/zoom for a graph over [space], plus the zoom pill: the visible range with a
 * reset ✕ while zoomed, and a loupe that arms one-shot select-to-zoom (so a plain
 * drag or click never zooms by surprise) and offers "Zoom to fit" [fitDists] when
 * that zooms in substantially.
 *
 * [initialView] (a content range) sets the view on mount; [onViewChange] reports
 * the visible range while zoomed, null at full range — together they carry a view
 * across graphs that replace each other.
 */
fun useGraphZoom(
    params: PZParams,
    space: NumericSpace,
    fitDists: List<ContinuousProbabilityDistribution>,
    initialView: ClosedFloatingPointRange<Double>? = null,
    onViewChange: ((ClosedFloatingPointRange<Double>?) -> Unit)? = null,
    /** Pill variant for compact header rows (see [ZoomPillProps.compact]). */
    compactPill: Boolean = false,
): GraphZoom {
    // Inverse of [visibleDomainRange]: an end inside the domain sat at the viewport
    // edge, one clamped to the domain boundary at the edge of its side padding.
    val initialState = initialView?.let { v ->
        val domain = params.contentDomain
        params.solveMapMap(List2(v.start, v.endInclusive), List2(
            if (v.start <= domain.start) params.sidePad else 0.0,
            if (v.endInclusive >= domain.endInclusive) params.viewportWidth - params.sidePad else params.viewportWidth))
    } ?: PZState(params)
    val (panZoomRE, state, ctl) = usePanZoom<HTMLElement>(params, initialState)
    var armed by useState(false)
    var selection by useState<ClosedFloatingPointRange<Double>>()
    ctl.dragMode = { if (armed) PZDragMode.SELECT else PZDragMode.PAN }
    ctl.onSelectionChange = { selection = it }
    ctl.onSelectZoom = { armed = false }
    useEffect(state.key()) {
        onViewChange?.invoke(if (state.zoom > 1.0) state.visibleDomainRange else null)
    }
    useDocumentEventListener(EventType<KeyboardEvent>("keydown"), enabled = armed) { e ->
        if (e.key == "Escape") armed = false
    }
    // Pressing anywhere but the graph or its pill backs out.
    val surfaceRef = useRef<HTMLElement>()
    val pillRef = useRef<HTMLElement>()
    useDocumentEventListener(EventType<PointerEvent>("pointerdown"), enabled = armed, capture = true) { ev ->
        val target = ev.target as? Node
        if (surfaceRef.current?.contains(target) != true && pillRef.current?.contains(target) != true) armed = false
    }
    val coarsePointer = useMediaQuery("(pointer: coarse)")

    // "Zoom to fit": all [fitDists]' 98% intervals, padded. Offered only when it would
    // zoom in substantially (a spike in a wide space); a mild zoom just confuses.
    val fitState = useMemo(fitDists.joinToString("|") { it.identify() }, params.key()) {
        val cis = fitDists.map { it.confidenceInterval(0.98) }
        if (cis.isEmpty()) return@useMemo null
        val lo = cis.minOf { it.start }
        val hi = cis.maxOf { it.endInclusive }
        val pad = (hi - lo) * 0.1
        val zoom = params.solveMapMap(List2(lo - pad, hi + pad),
            List2(params.sidePad, params.viewportWidth - params.sidePad)).zoom
        // Centered explicitly: when maxZoom clamps, solveMapMap would pin the left edge.
        params.solveZoomMap(zoom, (lo + hi) / 2, params.viewportWidth / 2).takeIf { it.zoom >= 2.0 }
    }

    val shown = state.visibleDomainRange
    val unitsPerPx = (shown.endInclusive - shown.start) / 50.0
    val pill = div.create {
        ref = pillRef
        ZoomPill {
            compact = compactPill
            zoomed = state.zoom > 1.0
            rangeLabel = "${space.formatAtResolution(shown.start, unitsPerPx, showUnit = false)} – " +
                space.formatAtResolution(shown.endInclusive, unitsPerPx)
            expanded = armed
            onToggle = { armed = !armed }
            onReset = { armed = false; ctl.state = PZState(state.params) }
            fitState?.takeIf { abs(ln(it.zoom / state.zoom)) > ln(1.25) || abs(it.pan - state.pan) > 20 }?.let { fit ->
                ButtonUnstyled {
                    css {
                        padding = if (compactPill) Padding(1.px, 6.px) else Padding(2.px, 7.px)
                        whiteSpace = WhiteSpace.nowrap
                        fontWeight = integer(600)
                        color = MainPalette.primary.color
                        borderRight = Border(1.px, LineStyle.solid, Color("#DDDDDD"))
                    }
                    onClick = { armed = false; ctl.state = fit }
                    +"Zoom to fit"
                }
            }
            div {
                css {
                    padding = if (compactPill) Padding(1.px, 6.px) else Padding(2.px, 8.px, 2.px, 5.px)
                    whiteSpace = WhiteSpace.nowrap
                    color = Color("#555555")
                }
                +if (coarsePointer) "Pinch to zoom"
                else "Drag to select a range · ${ZoomHint.modifierLabel}+scroll"
            }
        }
    }
    return GraphZoom(combineRefs(panZoomRE, surfaceRef), state, ctl, armed, { armed = false }, pill, selection)
}
