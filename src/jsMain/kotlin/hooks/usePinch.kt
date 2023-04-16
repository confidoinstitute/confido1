package hooks

import dom.events.Touch
import dom.events.TouchEvent
import dom.events.TouchList
import dom.html.HTMLElement
import kotlinx.js.get
import org.w3c.dom.events.Event
import react.MutableRefObject
import react.useMemo
import tools.confido.utils.*

data class PinchState(
    val target: HTMLElement,
    val touches: List2<Touch>,
) {
    val targetBoundingRect = target.getBoundingClientRect()
    val touchCoords = touches.map {
        List2(it.clientX - targetBoundingRect.left, it.clientY - targetBoundingRect.top)
    }
    val center = (touchCoords[0] `Z+` touchCoords[1]) Zdiv 2
    val touchFromCenter = touchCoords.map { it `Z-` center }
    val touchDiff = (touchCoords[1] `Z-` touchCoords[0])
    val dist = touchDiff.length()
}

typealias PinchUpdateHandler = (start: PinchState, current: PinchState, commit: Boolean) -> Unit
typealias PinchStartHandler = (PinchState) -> PinchUpdateHandler

fun TouchList.toList() = List(this.length) { this[it] }

class PinchController(var startHandler: PinchStartHandler) {
    var touchIds: List2<Double>?  = null
    var updateHandler: PinchUpdateHandler? = null
    var startState: PinchState? = null
    var lastState: PinchState? = null

    fun maybeCommit() {
        multiletNotNull(updateHandler, startState, lastState) { uh, ss, ls->
            uh(ss, ls, true)
        }
        updateHandler = null
        startState = null
        lastState = null
    }

    fun onTouchChange(e: Event) {
        if (e !is TouchEvent) return
        maybeCommit()
        if (e.touches.length == 2) {
            val touches = List2(e.touches.toList().sortedBy { it.clientX })
            val touchIds = touches.map { it.identifier }
            val state = PinchState(target = e.target as HTMLElement, touches = touches)
            this.startState = state
            this.lastState = state
            this.touchIds = touchIds
            this.updateHandler = this.startHandler(state)
        }
    }
    fun onTouchMove(e: Event) {
        if (e !is TouchEvent) return
        if (e.touches.length != 2) return
        multiletNotNull(this.touchIds, this.startState) { touchIds, startState->
            val newTouches = List2(e.touches.toList())
            if (touchIds.toSet() != newTouches.map{it.identifier}.toSet()) return
            // We want the touch points in the same order as in the start event
            // so that they can be matched up by index
            val sortedTouches = newTouches.sortedBy { touchIds.indexOf(it.identifier) }
            val newState = PinchState(e.target as HTMLElement, sortedTouches)
            this.updateHandler?.invoke(startState, newState, false)
            lastState = newState
        }
    }
}

/**
 * Helper to handle pinch gestures on an element.
 * You need to set touch-action: none CSS property on the element in order for this to work.
 */
fun <T:HTMLElement> usePinch(handler: PinchStartHandler): MutableRefObject<T> {
    val ctl = useMemo{ PinchController(handler) }
    ctl.startHandler = handler // must update handler to reflect captured variables
    return combineRefs(
        useEventListener("touchstart", "touchend", passive=false, callback = ctl::onTouchChange, preventDefault = true),
        useEventListener("touchmove", passive=false, callback = ctl::onTouchMove, preventDefault = true),
    )
}
