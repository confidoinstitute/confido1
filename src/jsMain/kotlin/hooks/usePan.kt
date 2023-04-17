package hooks

import dom.events.MouseEvent
import dom.events.PointerEvent
import dom.html.HTMLElement
import react.MutableRefObject
import react.useMemo
import tools.confido.utils.List2
import tools.confido.utils.multiletNotNull

val MouseEvent.offsetPos get() = List2(offsetX, offsetY)

class PanController(var startHandler: PanStartHandler) {
    // pointer id -> last known position
    val activePointers = mutableMapOf<Int, List2<Double>>()
    var panOrigin: List2<Double>? = null
    var panLast: List2<Double>? = null
    var updateHandler: PanUpdateHandler? = null
    fun onPointerChange(ev: org.w3c.dom.events.Event) {
        ev as PointerEvent
        val down = ev.type == "pointerdown"
        if (down)  {
            activePointers[ev.pointerId] = ev.offsetPos
            (ev.target as? HTMLElement)?.setPointerCapture(ev.pointerId)
        }
        else activePointers.remove(ev.pointerId)
        if (activePointers.size == 1) {
            startPan(activePointers.values.first())
        } else {
            endPan()
        }
    }
    fun startPan(pos: List2<Double>) {
        if (panOrigin != null) return
        panOrigin = pos
        panLast = pos
        updateHandler = startHandler?.invoke(pos)
    }
    fun endPan() {
        multiletNotNull(panOrigin, panLast) { po, pl ->
            updateHandler?.invoke(po, pl, true)
        }
        panOrigin = null
        updateHandler = null
    }
    fun onPointerMove(ev: org.w3c.dom.events.Event) {
        ev as PointerEvent
        if (ev.pointerId !in activePointers) return
        activePointers[ev.pointerId] = ev.offsetPos
        if (activePointers.keys == setOf(ev.pointerId)) {
            panOrigin?.let { panOrigin->
                panLast = ev.offsetPos
                updateHandler?.invoke(panOrigin, ev.offsetPos, false)
            }
        }
    }
}

typealias PanUpdateHandler = (start: List2<Double>, current: List2<Double>, commit: Boolean) -> Unit
typealias PanStartHandler = (List2<Double>) -> PanUpdateHandler


fun <T: HTMLElement> usePan(handler: PanStartHandler): MutableRefObject<T> {
    val ctl = useMemo{ PanController(handler) }
    ctl.startHandler = handler // must update handler to reflect captured variables
    return combineRefs(
        useEventListener("pointerdown", "pointerup", "pointercancel", passive=false, callback = ctl::onPointerChange, preventDefault = true),
        useEventListener("pointermove", passive=false, callback = ctl::onPointerMove, preventDefault = true),
    )
}