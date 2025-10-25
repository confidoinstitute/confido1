package hooks

import web.pointer.PointerEvent
import web.html.HTMLElement
import js.objects.Object
import js.objects.unsafeJso as jso
import react.RefObject
import react.useMemo
import tools.confido.utils.List2
import tools.confido.utils.`Z-`
import tools.confido.utils.length
import web.cssom.atrule.Pointer
import web.events.Event
import web.mouse.MAIN
import web.mouse.MouseButton
import web.pointer.POINTER_CANCEL
import web.pointer.POINTER_DOWN
import web.pointer.POINTER_MOVE
import web.pointer.POINTER_OUT
import web.pointer.POINTER_UP

/**
 * A ref-effect that will detec "pure click" events, i.e., click without drag or hold.
 * (there are some tolerances for accidental movement)
 */
fun <T: HTMLElement> usePureClick(allowTouch: Boolean = true, callback: (PointerEvent)->Unit): RefObject<T> {
    data class EventInfo(val offsetPos: List2<Double>, val timeStamp: Double)
    val ctl = useMemo{ object {
        var savedCallback = callback
        var savedAllowTouch = allowTouch
        val downEvents = mutableMapOf<Int, EventInfo>()
        fun onPointerDown(ev: Event) {
            ev as PointerEvent
            if (ev.pointerType == "touch" && !savedAllowTouch) return
            if (ev.pointerType == "mouse" && ev.button != MouseButton.MAIN) return
            downEvents[ev.pointerId] = EventInfo(ev.offsetPos, ev.timeStamp.toDouble())
            console.log("PUR DOWN ${ev.offsetPos.e1}")
        }
        fun onPointerMoveUp(ev: PointerEvent) {
            val downEv = downEvents[ev.pointerId] ?: return
            if ((ev.offsetPos `Z-` downEv.offsetPos).length() > 5
                || ev.timeStamp.toDouble() - downEv.timeStamp.toDouble() > 660.0) {
                downEvents.remove(ev.pointerId)
                console.log("PUR ABORT ${ev.target} ${ev.offsetPos.toTypedArray()} ${downEv.offsetPos.toTypedArray()} ${ev.timeStamp.toDouble() - downEv.timeStamp.toDouble() }")
                return
            }
            if (ev.type == PointerEvent.POINTER_UP) {
                console.log("PUR COMMIT ${downEv.offsetPos.e1}")
                savedCallback(ev)
            }
        }
        fun onPointerCancelOut(ev: PointerEvent) {
            console.log("PUR CANCEL")
            downEvents.remove(ev.pointerId)
        }
    }}
    ctl.savedCallback = callback
    ctl.savedAllowTouch = allowTouch
    return combineRefs(
        useEventListener(PointerEvent.POINTER_DOWN, callback=ctl::onPointerDown),
        useEventListener(PointerEvent.POINTER_MOVE, PointerEvent.POINTER_UP, callback=ctl::onPointerMoveUp),
        useEventListener(PointerEvent.POINTER_CANCEL, PointerEvent.POINTER_OUT, callback=ctl::onPointerCancelOut),
    )
}
