package components.redesign.questions

import browser.window
import components.redesign.basic.PropsWithElementSize
import components.redesign.basic.Stack
import components.redesign.basic.elementSizeWrapper
import csstype.*
import dom.html.HTMLElement
import emotion.css.ClassName
import emotion.react.css
import kotlinx.js.asList
import kotlinx.js.jso
import react.*
import react.dom.events.EventHandler
import react.dom.events.MouseEvent
import react.dom.html.ReactHTML.div
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.TruncatedNormalDistribution
import tools.confido.spaces.NumericSpace
import tools.confido.utils.mid
import tools.confido.utils.size
import tools.confido.utils.toFixed
import utils.breakLines
import kotlin.math.abs


external interface NumericPredInputProps : Props {
    var space: NumericSpace
    var dist: ContinuousProbabilityDistribution?
}

external interface NumericPredSliderProps : NumericPredInputProps, PropsWithElementSize {
    var zoomParams: ZoomParams?
    var onChange: ((TruncatedNormalDistribution) -> Unit)?
    var onCommit: ((TruncatedNormalDistribution) -> Unit)?
}

external interface NumericPredSliderInternalProps : NumericPredSliderProps {
    var zoomManager: SpaceZoomManager
}

val centerOrigin : Transform = translate((-50).pct, (-50).pct)

private val NumericPredSliderTrack = FC<NumericPredSliderInternalProps> {props->
    val zoomMgr = props.zoomManager
    div {
        css {
            height = 4.px
            // FIXME: This if from figma. Does it make sense to have alpha here or should it just be gray?
            backgroundColor = Color("#4c4c4c")
            borderRadius = 2.px
            position = Position.absolute
            left = (zoomMgr.leftPadVisible - 2).px
            right = (zoomMgr.rightPadVisible - 2).px
            top = 50.pct
            transform = translatey((-50).pct)
            zIndex = integer(1)
        }
    }
    zoomMgr.marks.forEach {value->
        div {
            css {
                position = Position.absolute
                top = 50.pct
                left = zoomMgr.space2canvasCssPx(value).px
                width = 2.px
                height = 2.px
                backgroundColor = NamedColor.white
                borderRadius = 1.px
                zIndex = integer(2)
                transform = centerOrigin
            }
        }
    }
}
private enum class ThumbKind(val color: String) {
    Left("#0066FF"),
    Center("#00CC2E"),
    Right("#0066FF"),
}
private external interface NumericPredSliderThumbProps: NumericPredSliderInternalProps {
    var kind: ThumbKind
    var pos: Double
    var onThumbChange: ((Double)->Unit)?
    var onThumbCommit: ((Double)->Unit)?
    var disabled: Boolean?
}
private class DragEventManager(
    val container: HTMLElement,
    var onDragStart: (() -> Unit)? = null,
    var onDrag: ((Double) -> Unit)? = null,
    var onDragEnd: ((Double) -> Unit)? = null,
) {
    companion object {
        var anyPressed: Boolean = false
    }
    var pressed: Boolean = false
    enum class PressType { MOUSE, TOUCH }
    var pressType: PressType = PressType.MOUSE
    var pressOffset: Double = 0.0
    var touchId: Double = 0.0

    fun doMove(clientX: Double) {
        if (!pressed) return
        val newPos = clientX - pressOffset - container.clientLeft
        this.onDrag?.invoke(newPos)
    }
    fun onWindowMouseMove(ev: org.w3c.dom.events.Event) {
        val mouseEvent = ev as org.w3c.dom.events.MouseEvent
        doMove(mouseEvent.clientX.toDouble())
        ev.stopPropagation()
        ev.preventDefault()
    }
    fun onWindowTouchMove(ev: org.w3c.dom.events.Event) {
        val touchEvent = ev as dom.events.TouchEvent
        val touch = touchEvent.touches.asList().firstOrNull { it.identifier == touchId }
        if (touch != null)
            doMove(touch.clientX)
        ev.stopPropagation()
        ev.preventDefault()
    }
    val windowEventOpts = jso<dynamic>{ capture=true; passive=false}

    fun doRelease(clientX: Double) {
        if (!pressed) return
        pressed = false
        anyPressed = false
        console.log("end thumb drag")
        uninstallWindowListeners()
        val newPos = clientX - pressOffset - container.clientLeft
        this.onDrag?.invoke(newPos)
        this.onDragEnd?.invoke(newPos)
    }
    fun onWindowMouseUp(ev: org.w3c.dom.events.Event) {
        val mouseEvent = ev as org.w3c.dom.events.MouseEvent
        doRelease(mouseEvent.clientX.toDouble())
        ev.stopPropagation()
        ev.preventDefault()
    }
    fun onWindowTouchEnd(ev: org.w3c.dom.events.Event) {
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
    val installedWindowListeners: MutableSet<EventListenerSpec> = mutableSetOf()
    fun installWindowListener(
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
    fun uninstallWindowListeners() {
        installedWindowListeners.forEach {
            window.removeEventListener(it.eventName, it.handler, it.options)
        }
        installedWindowListeners.clear()
    }
    fun startDrag(type: PressType, off: Double) {
        if (pressed) return
        if (anyPressed) return
        pressed = true
        anyPressed = true
        // offset from the center
        console.log(off)
        pressOffset = off
        pressType = type
        console.log("start thumb drag")
        onDragStart?.invoke()
    }
    fun onMouseDown(event: react.dom.events.MouseEvent<HTMLElement,*>) {
        val off = event.nativeEvent.offsetX - (event.target as HTMLElement).clientWidth / 2
        startDrag(PressType.MOUSE, off)
        installWindowListener("mousemove", this::onWindowMouseMove, windowEventOpts)
        installWindowListener("mouseup", this::onWindowMouseUp, windowEventOpts)
    }
    fun onTouchStart(event: react.dom.events.TouchEvent<HTMLElement>) {
        if (pressed) return
        startDrag(PressType.TOUCH, 0.0)
        touchId = event.changedTouches.item(0)?.identifier ?: 0.0
        installWindowListener("touchmove", this::onWindowTouchMove, windowEventOpts)
        installWindowListener("touchend", this::onWindowTouchEnd, windowEventOpts)
        (event.target as HTMLElement).focus()
        event.preventDefault()
        event.stopPropagation()
    }
}

private fun useDragEventManager(
    container: HTMLElement,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Double) -> Unit)? = null,
    onDragEnd: ((Double) -> Unit)? = null,
): DragEventManager {
    val mgr = useMemo { DragEventManager(container, onDrag=onDrag, onDragEnd=onDragEnd) }
    useEffect(onDrag,onDragEnd) {
        mgr.onDragStart = onDragStart
        mgr.onDrag = onDrag
        mgr.onDragEnd = onDragEnd
    }
    return mgr
}
private val NumericPredSliderThumb = FC<NumericPredSliderThumbProps> {props->
    val kind = props.kind
    val pos = props.pos
    val zoomMgr = props.zoomManager
    val posPx = zoomMgr.space2canvasCssPx(pos)
    val disabled = props.disabled ?: false
    var focused by useState(false)
    var pressed by useState(false)
    var pressOffset by useState(0.0)
    val signpostVisible = focused || pressed
    val svg = "/static/slider-${kind.name.lowercase()}-${if (disabled) "inactive" else "active"}.svg"
    val eventMgr = useDragEventManager(props.element,
        onDragStart = {pressed=true},
        onDrag = {props.onThumbChange?.invoke(zoomMgr.canvasCssPx2space(it))},
        onDragEnd = {pressed=false; props.onThumbCommit?.invoke(zoomMgr.canvasCssPx2space(it))},
        )
    div {
        css {
            position = Position.absolute
            width = 38.px
            height = 40.px
            top = 50.pct
            left = posPx.px
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
        tabIndex = 0 // make focusable
        onFocus = { focused = true }
        onBlur = { focused = false }
        onMouseDown = eventMgr::onMouseDown
        onTouchStart = eventMgr::onTouchStart


        //onTouchStart = { event->
        //    event.preventDefault() // do not autogenerate mousedown event (https://stackoverflow.com/a/31210694)
        //}
    }
        div {// signpost stem
            css {
                position = Position.absolute
                height = 120.px
                width = 2.px
                transform = translatex((-50).pct)
                backgroundColor = Color(kind.color)
                left = posPx.px
                bottom = 50.pct
                zIndex = integer(3)
                visibility = if (signpostVisible) Visibility.visible else Visibility.hidden
            }
        }
        div {
            css {
                position = Position.absolute
                transform = translatex(
                    if (posPx <= zoomMgr.viewportWidth / 2)
                        max((-50).pct, (-posPx).px)
                    else {
                        val rightSpace = zoomMgr.viewportWidth - posPx
                        min((-50).pct, "calc(${rightSpace}px - 100%)".unsafeCast<Length>())
                    }
                )
                backgroundColor = Color(kind.color)
                left = posPx.px
                bottom = 132.px
                zIndex = integer(4)
                borderRadius = 5.px
                padding = Padding(4.px,6.px)
                fontSize = 20.px
                lineHeight = 24.px
                fontFamily = FontFamily.sansSerif
                fontWeight = integer(700)
                color = NamedColor.white
                visibility = if (signpostVisible) Visibility.visible else Visibility.hidden
            }
            +pos.toFixed(2)
        }
}

fun binarySearch(initialRange: ClosedFloatingPointRange<Double>, desiredValue: Double, maxSteps: Int, f: (Double) -> Double): ClosedFloatingPointRange<Double> {
    var curRange = initialRange
    fun cmp(x: Double) = desiredValue.compareTo(f(x))
    for (step in 1..maxSteps) {
        if (cmp(curRange.endInclusive) == 1) curRange = curRange.start .. (2*curRange.endInclusive)
        else break
    }
    for (step in 1..maxSteps) {
        val mid = curRange.mid
        when (cmp(mid)) {
            0 -> return mid..mid
            1 -> curRange = mid..curRange.endInclusive // want higher
            -1 -> curRange = curRange.start..mid // want lower
        }
    }
    return curRange
}
fun findDistribution(space: NumericSpace, center: Double, ciWidth: Double): TruncatedNormalDistribution {
    val pseudoStdev = binarySearch(0.0..ciWidth, ciWidth, 30) {
        TruncatedNormalDistribution(space, center, it).confidenceInterval(0.8).size
    }.mid
    return TruncatedNormalDistribution(space, center, pseudoStdev)
}

val NumericPredSlider = elementSizeWrapper(FC<NumericPredSliderProps> { props->
    val space = props.space
    val propDist = props.dist as? TruncatedNormalDistribution
    var center by useState(propDist?.pseudoMean)
    var ciWidth by useState(propDist?.confidenceInterval(0.8)?.size)
    val ciRadius = ciWidth?.let { it / 2.0 }
    val zoomManager = SpaceZoomManager(props.space, props.elementWidth, props.zoomParams ?: ZoomParams())
    val minCIRadius = 20.0 / zoomManager.xScale // do not allow the thumbs to overlap too much
    val ci = if (center != null && ciWidth != null) {
        if (center!! + ciRadius!! > space.max) (space.max - ciWidth!!)..space.max
        else if (center!! - ciRadius < space.min) space.min..(space.min + ciWidth!!)
        else (center!! - ciRadius)..(center!! + ciRadius)
    } else null
    useEffect(propDist) {
        propDist?.let {
            center = propDist.pseudoMean
            ciWidth = propDist.confidenceInterval(0.8).size
        }
    }
    val dist = useMemo(space, center, ciWidth) {
        if (center != null && ciWidth != null) findDistribution(space, center!!, ciWidth!!)
        else null
    }
    useEffect(dist?.pseudoMean, dist?.pseudoStdev) {
        dist?.let { props.onChange?.invoke(dist) }
    }
    div {
        css {
            height = 40.px
            minHeight = 40.px
            flexShrink = number(0.0)
            position = Position.relative
            // overflowX = Overflow.hidden // FIXME apparently, this does not work
            // overflowY = Overflow.visible
        }
        NumericPredSliderTrack {
            +props
            this.zoomManager = zoomManager
        }
        if (dist != null) {
            NumericPredSliderThumb{
                +props
                this.zoomManager = zoomManager
                kind = ThumbKind.Left
                pos = ci!!.start
                onThumbChange = {
                    center?.let { center->
                        val effectivePos = minOf(it, center - minCIRadius)
                        val naturalRadius = center - effectivePos
                        if (center + naturalRadius > space.max) {
                            ciWidth = space.max - effectivePos
                        } else {
                            ciWidth = 2 * naturalRadius
                        }
                    }
                }
            }
            NumericPredSliderThumb{
                +props
                this.zoomManager = zoomManager
                kind = ThumbKind.Center
                pos = dist.pseudoMean
                onThumbChange = { center = it }
            }
            NumericPredSliderThumb{
                +props
                this.zoomManager = zoomManager
                kind = ThumbKind.Right
                pos = ci!!.endInclusive
                onThumbChange = {
                    center?.let { center->
                        val effectivePos = maxOf(it, center + minCIRadius)
                        val naturalRadius = effectivePos - center
                        if (center - naturalRadius < space.min) {
                            ciWidth = effectivePos - space.min
                        } else {
                            ciWidth = 2 * naturalRadius
                        }
                    }
                }
            }
        }
    }
}, ClassName {
    // overflowX = Overflow.hidden // FIXME apparently, this does not work
    // overflowY = Overflow.visible
})


val NumericPredInput = FC<NumericPredInputProps> { props->
    var zoomParams by useState(ZoomParams())
    val propDist = props.dist as? TruncatedNormalDistribution?
    var previewDist by useState(propDist)
    useEffect(propDist) { previewDist = propDist }
    Stack {
        css {
            overflowX = Overflow.hidden
        }
        NumericPredGraph {
            this.space = props.space
            this.dist = previewDist
            onZoomChange = { zoomParams = it }
        }
        NumericPredSlider {
            this.space = props.space
            this.zoomParams = zoomParams
            this.dist = props.dist
            this.onChange = {
                previewDist = it
            }
        }

    }
}