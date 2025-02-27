package utils.panzoom1d

import dom.events.WheelEvent
import dom.html.HTMLElement
import hooks.*
import io.ktor.util.*
import react.*
import tools.confido.utils.List2
import tools.confido.utils.size
import kotlin.lazy
import kotlin.math.abs


data class PZParams(
    val contentDomain: ClosedFloatingPointRange<Double>,
    val viewportWidth: Double,
    val sidePad: Double = 0.0,
    val maxZoom: Double = 10.0
) {
    fun key() = "${contentDomain.start}:${contentDomain.endInclusive}:$viewportWidth:$sidePad:$maxZoom"
    val contentWidth get() = contentDomain.size
    val paperWidthUnzoomed by lazy { (viewportWidth - 2* sidePad) }

    /**
     * Given a zoom level and a mapping content coord -> viewport coord, return
     * a PZState with the given zoom and a pan consistent with that mapping (if possible).
     */
    fun solveZoomMap(zoom: Double, contentX: Double, viewportX: Double): PZState {
        val protoState = PZState(this, zoom, 0.0)
        val paperX = contentToRelative(contentX) * protoState.paperWidth
        val pan = (paperX - viewportX).coerceIn(protoState.panRange)
        return PZState(this, zoom, pan)
    }

    /** Given a list of two (contentX, viewportX) mappings, find zoom state that
     * fit these mappings.
     */
    fun solveMapMap(contentCoords: List2<Double>, viewportCoords: List2<Double>): PZState {
        val contentDist = abs(contentCoords.e2 - contentCoords.e1)
        val viewportDist = abs(viewportCoords.e2 - viewportCoords.e1)
        val paperDist = viewportDist // same scale
        // Firstly, determine zoom
        val paperDistUnzoomed = contentDist / contentWidth * paperWidthUnzoomed
        val zoom = (paperDist / paperDistUnzoomed).coerceIn(1.0..maxZoom)
        val c1 = contentCoords.min()
        val v1 = viewportCoords.min()
        return solveZoomMap(zoom, c1, v1)
    }

    fun contentToRelative(contentX: Double) = (contentX - contentDomain.start) / contentWidth
    fun relativeToContent(relativeX: Double) = relativeX * contentWidth + contentDomain.start
}

data class PZState(
    val params: PZParams,
    val zoom: Double = 1.0,
    val pan: Double = -params.sidePad, // in pixels at current zoom level
) {
    /** A string uniquely describing objects content, usable as react useEffect dependency etc.*/
    fun key() = "${params.key()}:$zoom:$pan"

    // three coordinate systems:
    //  - content coordinates (abstract, range given by parmas.contentDomain)
    //  - viewport coordinates (visible part) in CSS pixels
    //  - paper coordinates (an imaginary canvas containing the whole content at current zoom level but NOT side padding), in pixels

    fun viewportToPaper(viewportX: Double) =
        (viewportX + panEffective).coerceIn(0.0, paperWidth)
    fun paperToViewport(paperX: Double) =
        (paperX - panEffective).coerceIn(0.0, params.viewportWidth)

    fun paperDistToContent(paperX: Double) =
        (paperX / paperWidth * params.contentWidth).coerceIn(0.0, params.contentWidth)
    fun paperToContent(paperX: Double) =
        (paperX / paperWidth * params.contentWidth + params.contentDomain.start).coerceIn(params.contentDomain)
    fun contentToPaper(contentX: Double) =
        (contentX.coerceIn(params.contentDomain) - params.contentDomain.start) / params.contentWidth * paperWidth
    fun viewportToContent(viewportX: Double) = paperToContent(viewportToPaper(viewportX))
    fun contentToViewport(contentX: Double) = paperToViewport(contentToPaper(contentX))

    val paperWidth by lazy { params.paperWidthUnzoomed * zoom } // width of the whole content, in css pixels, at current zoom
    val minPan by lazy { -params.sidePad }
    val maxPan by lazy { maxOf(paperWidth + params.sidePad - params.viewportWidth, minPan) }
    val panRange by lazy { minPan..maxPan }

    val panEffective by lazy { pan.coerceIn(panRange) }
    val leftPadVisible by lazy { maxOf(- panEffective, 0.0) }
    val rightPadVisible by lazy { maxOf(params.viewportWidth - (paperWidth - panEffective), 0.0) }

    val visibleContentRange by lazy { viewportToContent(0.0)..viewportToContent(params.viewportWidth) }
    val visibleContentWidth by lazy { params.viewportWidth - leftPadVisible - rightPadVisible }
}

open class PZController(var params: PZParams, initialState: PZState = PZState(params), var onChange: ((PZState) -> Unit)?) {
    fun onPinchStart(startPinch: PinchState): PinchUpdateHandler {
        val startState = state
        val startFingerContentX = startPinch.touchCoords.map { state.viewportToContent(it.e1) }
        return {
            _, newPinch, commit ->

            val newFingerViewportX = newPinch.touchCoords.map { it.e1 }
            val newState = params.solveMapMap(startFingerContentX, newFingerViewportX)
            state = newState
        }
    }

    fun onPanStart(startPos: List2<Double>): PanUpdateHandler {
        val startState = state
        return { _, newPos, commit ->
            val newPan = (startState.pan + (startPos[0] - newPos[0])).coerceIn(state.panRange)

            val newState = state.copy(pan = newPan)
            state = newState
        }
    }

    fun onWheel(ev: org.w3c.dom.events.Event) {
        ev as WheelEvent
        val mouseContent = state.viewportToContent(ev.offsetX)
        val zoomDelta = kotlin.math.exp(ev.deltaY * -0.005)
        val newZoom = (state.zoom * zoomDelta).coerceIn(1.0..params.maxZoom)
        val newState = params.solveZoomMap(newZoom, mouseContent, ev.offsetX)
        state = newState
    }

    fun updateParams(newParams: PZParams) {
        if (newParams != params) {
            params = newParams
            state = state.copy(params = newParams)
            onChange?.invoke(state)
        }
    }

    var state: PZState = initialState
        get() = field
        set(value)  { field = value; onChange?.invoke(value) }
}

data class PZRet<T: HTMLElement>(
    val re: MutableRefObject<T>,
    val state: PZState,
    val ctl: PZController
) {
}

fun <T:HTMLElement> usePanZoom(params: PZParams, initialState: PZState = PZState(params),
               onZoomChange: ((PZState)->Unit)?=null): PZRet<T> {
    var zoomState by useState(initialState)
    val ctl = useMemo { PZController(params, initialState, onChange = { zoomState = it; onZoomChange?.invoke(it) }) }
    useEffect(params.key()) {
        ctl.updateParams(params)
    }
    val refEffect = combineRefs<T>(
        usePinch(ctl::onPinchStart),
        usePan(ctl::onPanStart),
        useEventListener("wheel", callback = ctl::onWheel, passive = false, preventDefault = true),
    )
    return PZRet(refEffect, zoomState, ctl)
}

