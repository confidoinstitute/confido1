package components.redesign.questions

import browser.window
import csstype.*
import dom.events.TouchEvent
import dom.html.HTMLElement
import emotion.react.css
import kotlinx.js.asList
import kotlinx.js.jso
import react.*
import react.dom.html.ReactHTML
import tools.confido.utils.toFixed
import kotlin.math.abs

enum class ThumbKind(val signpostColor: String?) {
    Left("#0066FF"),
    Center("#00CC2E"),
    Right("#0066FF"),
    Binary(null), // no signpost
}

fun ThumbKind.svg(disabled: Boolean) =
    "/static/slider-${name.lowercase()}-${if (disabled) "inactive" else "active"}.svg"


external interface SliderThumbProps : Props {
    var containerElement: HTMLElement
    var zoomManager: SpaceZoomManager
    var pos: Double
    var onThumbChange: ((Double)->Unit)?
    var onThumbCommit: ((Double)->Unit)?
    var disabled: Boolean?
    var kind: ThumbKind
}
private class DragEventManager(
    val container: HTMLElement,
    val draggableRef: RefObject<HTMLElement>,
    var onClick: (()->Unit)? = null,
    var onDragStart: (() -> Unit)? = null,
    var onDrag: ((Double) -> Unit)? = null,
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
            this.onDrag?.invoke(newPos)
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
            this.onDrag?.invoke(newPos)
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
    fun onMouseDown(event: react.dom.events.MouseEvent<HTMLElement,*>) {
        val off = event.nativeEvent.offsetX - (event.target as HTMLElement).clientWidth / 2
        startDrag(PressType.MOUSE, off, event.nativeEvent.clientX.toDouble())
        installWindowListener("mousemove", this::onWindowMouseMove, windowEventOpts)
        installWindowListener("mouseup", this::onWindowMouseUp, windowEventOpts)
        event.preventDefault()
        event.stopPropagation()
    }
    fun onTouchStart(event: org.w3c.dom.events.Event) {
        if (pressed) return
        val draggable = draggableRef.current?:return
        val touchEvent = (event as TouchEvent)
        val touch = touchEvent.changedTouches.item(0) ?: return
        //console.log("TS", touch.clientX, draggable.clientLeft, draggable.getBoundingClientRect().left)
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

private fun useDragEventManager(
    container: HTMLElement,
    draggableRef: react.RefObject<HTMLElement>,
    onClick: (() -> Unit)? = null,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Double) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
): DragEventManager {
    val mgr = useMemo { DragEventManager(container, draggableRef=draggableRef,
        onClick=onClick, onDragStart=onDragStart, onDrag=onDrag, onDragEnd=onDragEnd) }
    useEffect(onClick,onDragStart,onDrag,onDragEnd) {
        mgr.onClick = { console.log("click"); draggableRef.current?.focus(); onClick?.invoke() }
        mgr.onDragStart = onDragStart
        mgr.onDrag = onDrag
        mgr.onDragEnd = onDragEnd
    }
    // We must use this beacause react's onTouchStart cannot create non-passive listener
    useEffect(draggableRef.current) {
        val handler = mgr::onTouchStart
        val opts = jso<dynamic>{ passive = false }
        draggableRef.current?.addEventListener("touchstart", handler, opts)
        cleanup {
            draggableRef.current?.removeEventListener("touchstart", handler, opts)
        }
    }
    return mgr
}
val SliderThumb = FC<SliderThumbProps> {props->
    val pos = props.pos
    val zoomMgr = props.zoomManager
    val posPx = zoomMgr.space2canvasCssPx(pos)
    val disabled = props.disabled ?: false
    var focused by useState(false)
    var dragFocused by useState(false)
    var pressed by useState(false)
    var pressOffset by useState(0.0)
    val signpostVisible = (focused  && !dragFocused) || pressed
    val kind = props.kind
    val svg = kind.svg(disabled)
    val thumbRef = useRef<HTMLElement>()
    val eventMgr = useDragEventManager(props.containerElement,
        thumbRef,
        onDragStart = {
                            pressed=true
                            if (!focused) {
                                thumbRef.current?.focus()
                                dragFocused = true
                            }
                      },
        onDrag = {props.onThumbChange?.invoke(zoomMgr.canvasCssPx2space(it))},
        onDragEnd = {pressed=false},
        onClick = {
            thumbRef.current?.focus()
            dragFocused = false
        }
    )
    ReactHTML.div {
        css {
            position = Position.absolute
            width = 38.px
            height = 40.px
            top = 50.pct
            transform = centerOrigin
            backgroundImage = url(svg)
            backgroundPositionX = BackgroundPositionX.center
            zIndex = integer(if (kind == ThumbKind.Center) 5 else 4)
            "&:focus" {
                // some browsers have default styling for focused elements, undo it
                // (we signal focus by showing the signpost)
                border = None.none
                outline = None.none
            }
        }
        ref = thumbRef
        style = jso {
            left = posPx.px
        }
        tabIndex = 0 // make focusable
        onFocus = { focused = true }
        onBlur = { focused = false; dragFocused = false; }
        onMouseDown = eventMgr::onMouseDown
        //onTouchStart = eventMgr::onTouchStart


        //onTouchStart = { event->
        //    event.preventDefault() // do not autogenerate mousedown event (https://stackoverflow.com/a/31210694)
        //}
    }
    if (kind.signpostColor != null) {
        ReactHTML.div {// signpost stem
            css {
                position = Position.absolute
                height = 120.px
                width = 2.px
                transform = translatex((-50).pct)
                backgroundColor = Color(kind.signpostColor)
                bottom = 50.pct
                zIndex = integer(3)
                visibility = if (signpostVisible) Visibility.visible else Visibility.hidden
            }
            style = jso {
                left = posPx.px
            }
        }
        ReactHTML.div {
            css {
                position = Position.absolute
                backgroundColor = Color(kind.signpostColor)
                bottom = 132.px
                zIndex = integer(4)
                borderRadius = 5.px
                padding = Padding(4.px, 6.px)
                fontSize = 20.px
                lineHeight = 24.px
                fontFamily = FontFamily.sansSerif
                fontWeight = integer(700)
                color = NamedColor.white
                visibility = if (signpostVisible) Visibility.visible else Visibility.hidden
            }
            style = jso {
                transform = translatex(
                    if (posPx <= zoomMgr.viewportWidth / 2)
                        max((-50).pct, (-posPx).px)
                    else {
                        val rightSpace = zoomMgr.viewportWidth - posPx
                        min((-50).pct, "calc(${rightSpace}px - 100%)".unsafeCast<Length>())
                    }
                )
                left = posPx.px
            }
            +pos.toFixed(2)
        }
    }
}
