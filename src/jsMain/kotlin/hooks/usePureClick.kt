package hooks

import dom.events.PointerEvent
import dom.events.PointerEventInit
import dom.html.HTMLElement
import kotlinx.js.Object
import kotlinx.js.jso
import react.MutableRefObject
import react.useMemo
import tools.confido.utils.List2
import tools.confido.utils.`Z-`
import tools.confido.utils.length
import web.events.Event

/**
 * A ref-effect that will detec "pure click" events, i.e., click without drag or hold.
 * (there are some tolerances for accidental movement)
 */
fun <T: HTMLElement> usePureClick(allowTouch: Boolean = true, callback: (PointerEvent)->Unit): MutableRefObject<T> {
    data class EventInfo(val offsetPos: List2<Double>, val timeStamp: Double)
    val ctl = useMemo{ object {
        var savedCallback = callback
        var savedAllowTouch = allowTouch
        val downEvents = mutableMapOf<Int, EventInfo>()
        fun onPointerDown(ev: Event) {
            ev as PointerEvent
            if (ev.pointerType == "touch" && !savedAllowTouch) return
            if (ev.pointerType == "mouse" && ev.button.toInt() != 0) return
            downEvents[ev.pointerId] = EventInfo(ev.offsetPos, ev.timeStamp.toDouble())
            console.log("PUR DOWN ${ev.offsetPos.e1}")
        }
        fun onPointerMoveUp(ev: Event) {
            ev as PointerEvent
            val downEv = downEvents[ev.pointerId] ?: return
            if ((ev.offsetPos `Z-` downEv.offsetPos).length() > 5
                || ev.timeStamp.toDouble() - downEv.timeStamp.toDouble() > 660.0) {
                downEvents.remove(ev.pointerId)
                console.log("PUR ABORT ${ev.target} ${ev.offsetPos.toTypedArray()} ${downEv.offsetPos.toTypedArray()} ${ev.timeStamp.toDouble() - downEv.timeStamp.toDouble() }")
                return
            }
            if (ev.type == "pointerup") {
                console.log("PUR COMMIT ${downEv.offsetPos.e1}")
                savedCallback(ev)
            }
        }
        fun onPointerCancelOut(ev: org.w3c.dom.events.Event) {
            ev as PointerEvent
            console.log("PUR CANCEL")
            downEvents.remove(ev.pointerId)
        }
    }}
    ctl.savedCallback = callback
    ctl.savedAllowTouch = allowTouch
    return combineRefs(
        useEventListener("pointerdown", callback=ctl::onPointerDown),
        useEventListener("pointermove", "pointerup", callback=ctl::onPointerMoveUp),
        useEventListener("pointercancel", "pointerout", callback=ctl::onPointerCancelOut),
    )
}
