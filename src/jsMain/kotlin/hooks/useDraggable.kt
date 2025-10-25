package hooks

import js.array.asList
import web.window.window
import web.mouse.MouseEvent
import web.touch.TouchEvent
import web.html.HTMLElement
import js.objects.unsafeJso as jso
import react.*
import web.events.Event
import web.events.EventType
import web.events.addEventHandler
import web.events.removeEventListener
import web.mouse.MOUSE_DOWN
import web.mouse.MOUSE_MOVE
import web.mouse.MOUSE_UP
import web.pointer.POINTER_DOWN
import web.pointer.POINTER_MOVE
import web.pointer.POINTER_UP
import web.pointer.PointerEvent
import web.touch.TOUCH_END
import web.touch.TOUCH_MOVE
import web.touch.TOUCH_START
import kotlin.math.abs

private class DragEventManager<T: HTMLElement>(
    val container: HTMLElement,
    val draggableRef: RefObject<T>,
    var onClick: (()->Unit)? = null,
    var onDragStart: (() -> Unit)? = null,
    var onDrag: ((Double, Boolean) -> Unit)? = null,
    var onDragEnd: (() -> Unit)? = null,
) {
    companion object {
        var anyPressed: Boolean = false
    }
    var pressed: Boolean = false
    enum class PressType { MOUSE, TOUCH }
    var pressType: PressType = PressType.MOUSE
    var pressOffset: Double = 0.0
    var touchId: Int = 0
    var maxDist: Double = 0.0
    var startClientX: Double = 0.0

    private fun doMove(clientX: Double) {
        if (!pressed) return
        val newPos = clientX - pressOffset - container.getBoundingClientRect().left
        maxDist = maxOf(maxDist, abs(clientX - startClientX))
        if (maxDist >= 3)
            this.onDrag?.invoke(newPos, false)
    }
    private fun onWindowMouseMove(ev: MouseEvent) {
        doMove(ev.clientX.toDouble())
        ev.stopPropagation()
        ev.preventDefault()
    }
    private fun onWindowTouchMove(ev: TouchEvent) {
        val touch = ev.touches.asList().firstOrNull { it.identifier == touchId }
        if (touch != null)
            doMove(touch.clientX)
        ev.stopPropagation()
        ev.preventDefault()
    }
    val windowEventOpts = jso<dynamic>{ capture=true; passive=false}

    private fun doRelease(clientX: Double) {
        if (!pressed) return
        pressed = false
        anyPressed = false
        uninstallWindowListeners()
        val newPos = clientX - pressOffset - container.getBoundingClientRect().left
        maxDist = maxOf(maxDist, abs(clientX - startClientX))
        console.log("end thumb drag $maxDist")
        if (maxDist < 3) {
            this.onClick?.invoke()
        } else {
            this.onDrag?.invoke(newPos, true)
        }
        this.onDragEnd?.invoke()
    }
    private fun onWindowMouseUp(ev: MouseEvent) {
        doRelease(ev.clientX.toDouble())
        ev.stopPropagation()
        ev.preventDefault()
    }
    private fun onWindowTouchEnd(ev: TouchEvent) {
        val touch = ev.changedTouches.asList().firstOrNull { it.identifier == touchId }
        if (touch != null)
            doRelease(touch.clientX)
        ev.stopPropagation()
        ev.preventDefault()
    }

    data class EventListenerSpec(
        val eventType: EventType<Event>,
        val handler: ((Event)->Unit),
        val options: dynamic,
    )
    private val installedWindowListeners: MutableSet<EventListenerSpec> = mutableSetOf()
    private fun  <E: Event,>installWindowListener(
        eventType: EventType<Event>,
        handler: ((E)->Unit),
        options: dynamic,
    ) {
        // We have to save the exact parameters used to call addEventHandler so that
        // we can later call removeEventHandler with the same parameters. Especially,
        // removeEventHandler checks function identity. And Kotlin generates a new
        // wrapper function each time you reference this::method, so
        // removeEventHandler(this::method) would never work
        installedWindowListeners.add(EventListenerSpec(eventType, handler.unsafeCast<(Event)->Unit>(), options))
        window.addEventHandler(eventType, options, handler)
    }
    private fun uninstallWindowListeners() {
        installedWindowListeners.forEach {
            window.removeEventListener(it.eventType, it.handler, it.options)
        }
        installedWindowListeners.clear()
    }
    fun startDrag(type: PressType, off: Double, clientX: Double) {
        if (pressed) return
        if (anyPressed) return
        pressed = true
        anyPressed = true
        startClientX = clientX
        maxDist = 0.0
        // offset from the center
        console.log(off)
        pressOffset = off
        pressType = type
        console.log("start thumb drag")
        onDragStart?.invoke()
    }
    fun onMouseDown(event: MouseEvent) {
        val off = event.offsetX - (event.target as HTMLElement).clientWidth / 2
        startDrag(PressType.MOUSE, off, event.clientX.toDouble())
        installWindowListener(MouseEvent.MOUSE_MOVE, this::onWindowMouseMove, windowEventOpts)
        installWindowListener(MouseEvent.MOUSE_UP, this::onWindowMouseUp, windowEventOpts)
        event.preventDefault()
        event.stopPropagation()
    }
    fun onTouchStart(event: TouchEvent) {
        if (pressed) return
        val touch = event.changedTouches.item(0) ?: return
        //PPdraggable.style.outline="2px solid red"
        // XXX for some reason, using event.target does not work
        val br = (event.target as HTMLElement).getBoundingClientRect()
        val midX = br.left + br.width / 2
        val off = touch.clientX - midX
        startDrag(PressType.TOUCH, off, touch.clientX)
        touchId = touch.identifier
        installWindowListener(TouchEvent.TOUCH_MOVE, this::onWindowTouchMove, windowEventOpts)
        installWindowListener(TouchEvent.TOUCH_END, this::onWindowTouchEnd, windowEventOpts)
        event.preventDefault()
        event.stopPropagation()
    }
}

fun <T: HTMLElement> useDraggable(
    container: HTMLElement,
    onClick: (() -> Unit)? = null,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Double, Boolean) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
): RefObject<T> {
    val draggableRef = useRef<T>()
    val mgr = useMemo { DragEventManager(container, draggableRef=draggableRef,
        onClick=onClick, onDragStart=onDragStart, onDrag=onDrag, onDragEnd=onDragEnd) }
    useEffect(onClick,onDragStart,onDrag,onDragEnd) {
        mgr.onClick = { console.log("click"); draggableRef.current?.focus(); onClick?.invoke() }
        mgr.onDragStart = onDragStart
        mgr.onDrag = onDrag
        mgr.onDragEnd = onDragEnd
    }
    val ref = combineRefs(
        draggableRef,
        useEventListener(MouseEvent.MOUSE_DOWN, callback =  mgr::onMouseDown, passive = false, preventDefault = true),
        useEventListener(TouchEvent.TOUCH_START, callback =  mgr::onTouchStart, passive = false, preventDefault = true),
        // XXX We currently do not use pointer events here (maybe we should) but we need to prevent
        // them from propagation otherwise this breaks horribly if the slider is e.g. embedded in
        // a PanZoom1D
        useEventListener<HTMLElement, PointerEvent>(PointerEvent.POINTER_DOWN, passive=false) { ev-> ev.stopPropagation()},
        useEventListener<HTMLElement, PointerEvent>(PointerEvent.POINTER_MOVE, passive=false) { ev-> ev.stopPropagation()},
        useEventListener<HTMLElement, PointerEvent>(PointerEvent.POINTER_UP, passive=false) { ev-> ev.stopPropagation()},
    )
    return ref
}
