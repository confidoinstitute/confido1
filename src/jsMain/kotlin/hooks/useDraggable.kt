package hooks

import browser.window
import dom.events.TouchEvent
import dom.html.HTMLElement
import kotlinx.js.asList
import kotlinx.js.jso
import react.*
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
    var touchId: Double = 0.0
    var maxDist: Double = 0.0
    var startClientX: Double = 0.0

    private fun doMove(clientX: Double) {
        if (!pressed) return
        val newPos = clientX - pressOffset - container.getBoundingClientRect().left
        maxDist = maxOf(maxDist, abs(clientX - startClientX))
        if (maxDist >= 3)
            this.onDrag?.invoke(newPos, false)
    }
    private fun onWindowMouseMove(ev: org.w3c.dom.events.Event) {
        val mouseEvent = ev as org.w3c.dom.events.MouseEvent
        doMove(mouseEvent.clientX.toDouble())
        ev.stopPropagation()
        ev.preventDefault()
    }
    private fun onWindowTouchMove(ev: org.w3c.dom.events.Event) {
        val touchEvent = ev as dom.events.TouchEvent
        val touch = touchEvent.touches.asList().firstOrNull { it.identifier == touchId }
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
    private fun onWindowMouseUp(ev: org.w3c.dom.events.Event) {
        val mouseEvent = ev as org.w3c.dom.events.MouseEvent
        doRelease(mouseEvent.clientX.toDouble())
        ev.stopPropagation()
        ev.preventDefault()
    }
    private fun onWindowTouchEnd(ev: org.w3c.dom.events.Event) {
        val touchEvent = ev as dom.events.TouchEvent
        val touch = ev.changedTouches.asList().firstOrNull { it.identifier == touchId }
        if (touch != null)
            doRelease(touch.clientX)
        ev.stopPropagation()
        ev.preventDefault()
    }

    data class EventListenerSpec(
        val eventName: String,
        val handler: ((org.w3c.dom.events.Event)->Unit)?,
        val options: dynamic,
    )
    private val installedWindowListeners: MutableSet<EventListenerSpec> = mutableSetOf()
    private fun installWindowListener(
        eventName: String,
        handler: ((org.w3c.dom.events.Event)->Unit)?,
        options: dynamic,
    ) {
        // We have to save the exact parameters used to call addEventHandler so that
        // we can later call removeEventHandler with the same parameters. Especially,
        // removeEventHandler checks function identity. And Kotlin generates a new
        // wrapper function each time you reference this::method, so
        // removeEventHandler(this::method) would never work
        installedWindowListeners.add(EventListenerSpec(eventName, handler, options))
        window.addEventListener(eventName, handler, options)
    }
    private fun uninstallWindowListeners() {
        installedWindowListeners.forEach {
            window.removeEventListener(it.eventName, it.handler, it.options)
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
    fun onMouseDown(event: org.w3c.dom.events.Event) {
        console.log("mousedown1")
        event as org.w3c.dom.events.MouseEvent
        console.log("mousedown2")
        val off = event.offsetX - (event.target as HTMLElement).clientWidth / 2
        startDrag(PressType.MOUSE, off, event.clientX.toDouble())
        installWindowListener("mousemove", this::onWindowMouseMove, windowEventOpts)
        installWindowListener("mouseup", this::onWindowMouseUp, windowEventOpts)
        event.preventDefault()
        event.stopPropagation()
    }
    fun onTouchStart(event: org.w3c.dom.events.Event) {
        if (pressed) return
        val touchEvent = (event as TouchEvent)
        val touch = touchEvent.changedTouches.item(0) ?: return
        //PPdraggable.style.outline="2px solid red"
        // XXX for some reason, using event.target does not work
        val br = (event.target as HTMLElement).getBoundingClientRect()
        val midX = br.left + br.width / 2
        val off = touch.clientX - midX
        startDrag(PressType.TOUCH, off, touch.clientX)
        touchId = touch.identifier
        installWindowListener("touchmove", this::onWindowTouchMove, windowEventOpts)
        installWindowListener("touchend", this::onWindowTouchEnd, windowEventOpts)
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
): MutableRefObject<T> {
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
        useEventListener("mousedown", callback =  mgr::onMouseDown, passive = false, preventDefault = true),
        useEventListener("touchstart", callback =  mgr::onTouchStart, passive = false, preventDefault = true),
    )
    return ref
}
