package components.redesign.questions

import components.redesign.basic.PropsWithElementSize
import components.redesign.basic.Stack
import components.redesign.basic.elementSizeWrapper
import csstype.*
import emotion.css.ClassName
import emotion.react.css
import kotlinx.js.jso
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.TruncatedNormalDistribution
import tools.confido.spaces.NumericSpace
import tools.confido.utils.mid
import tools.confido.utils.size
import kotlin.math.abs


external interface NumericPredInputProps : PredictionInputProps {
}

external interface NumericPredSliderProps : NumericPredInputProps, PropsWithElementSize {
    var zoomParams: ZoomParams?
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
    val space = props.space as NumericSpace
    val propDist = props.dist as? TruncatedNormalDistribution
    var center by useState(propDist?.pseudoMean)
    var ciWidth by useState(propDist?.confidenceInterval(0.8)?.size)
    var dragging by useState(false)
    val ciRadius = ciWidth?.let { it / 2.0 }
    val zoomManager = SpaceZoomManager(space, props.elementWidth, props.zoomParams ?: ZoomParams())
    val minCIRadius = 20.0 / zoomManager.xScale // do not allow the thumbs to overlap too much
    val ci = if (center != null && ciWidth != null) {
        if (center!! + ciRadius!! > space.max) (space.max - ciWidth!!)..space.max
        else if (center!! - ciRadius < space.min) space.min..(space.min + ciWidth!!)
        else (center!! - ciRadius)..(center!! + ciRadius)
    } else null
    var didChange by useState(false)
    useEffectOnce { console.log("SLIDER INIT") }
    useEffect(propDist?.pseudoMean, propDist?.pseudoStdev) {
        console.log("new propdist ${propDist?.pseudoMean} ${propDist?.pseudoStdev}")
        propDist?.let {
            center = propDist.pseudoMean
            ciWidth = propDist.confidenceInterval(0.8).size
            didChange = false
        }
    }
    val dist = useMemo(space, center, ciWidth) {
        if (center != null && ciWidth != null) findDistribution(space, center!!, ciWidth!!)
        else null
    }
    //useEffect(dist?.pseudoMean, dist?.pseudoStdev, didChange) {
    //    if (didChange) dist?.let { props.onChange?.invoke(dist) }
    //}
    fun update(newCenter: Double, newCIWidth: Double, isCommit: Boolean) {
        val newDist = findDistribution(space, newCenter, newCIWidth)
        props.onChange?.invoke(newDist)
        if (isCommit)
            props.onCommit?.invoke(newDist)
    }
    //useEffect(dist?.pseudoMean, dist?.pseudoStdev, dragging) {
    //    if (!dragging) dist?.let { props.onCommit?.invoke(dist) }
    //}
    div {
        key="sliderArea"
        css {
            height = 40.px
            minHeight = 40.px
            flexShrink = number(0.0)
            position = Position.relative
            // overflowX = Overflow.hidden // FIXME apparently, this does not work
            // overflowY = Overflow.visible
        }
        if (dist != null)
            SliderTrack {
                +props
                this.zoomManager = zoomManager
            }
        if (dist != null)
            SliderThumb{
                key = "thumb_left"
                this.containerElement = props.element
                this.zoomManager = zoomManager
                kind = ThumbKind.Left
                pos = ci!!.start
                onDrag = { pos, isCommit ->
                    center?.let { center->
                        val effectivePos = minOf(pos, center - minCIRadius)
                        val naturalRadius = center - effectivePos
                        val newCIWidth = if (center + naturalRadius > space.max) {
                             space.max - effectivePos
                        } else {
                             2 * naturalRadius
                        }
                        ciWidth = newCIWidth
                        didChange = true
                        update(center, newCIWidth, isCommit)
                    }
                }
                onDragStart = { dragging = true }
                onDragEnd = {
                    dragging = false
                }
            }
        if (center != null)
            SliderThumb{
                key = "thumb_center"
                this.containerElement = props.element
                this.zoomManager = zoomManager
                kind = ThumbKind.Center
                pos = center!!
                onDrag = { pos, isCommit ->
                    center = pos
                    didChange = true
                    if (ciWidth != null) update(pos, ciWidth!!, isCommit)
                }
                onDragStart = { dragging = true }
                onDragEnd = { dragging = false }
            }
        if (dist != null)
            SliderThumb{
                key = "thumb_right"
                this.containerElement = props.element
                this.zoomManager = zoomManager
                kind = ThumbKind.Right
                pos = ci!!.endInclusive
                onDrag = { pos, isCommit->
                    center?.let { center->
                        val effectivePos = maxOf(pos, center + minCIRadius)
                        val naturalRadius = effectivePos - center
                        val newCIWidth = if (center - naturalRadius < space.min) {
                            effectivePos - space.min
                        } else {
                             2 * naturalRadius
                        }
                        ciWidth = newCIWidth
                        didChange = true
                        update(center, newCIWidth, isCommit)
                    }
                }
                onDragStart = { dragging = true }
                onDragEnd = { dragging = false }
            }
        if (center == null) {
            div {
                key = "setCenter"
                css {
                    fontFamily = FontFamily.sansSerif
                    fontWeight = integer(600)
                    fontSize = 15.px
                    lineHeight = 18.px
                    display = Display.flex
                    alignItems = AlignItems.center
                    textAlign = TextAlign.center
                    justifyContent = JustifyContent.center
                    color = Color("#6319FF")
                    flexDirection = FlexDirection.column
                    height = 100.pct
                    cursor = Cursor.default
                }
                +"Tap here to create an estimate"// TODO choose "tap"/"click" based on device
                onClick = { ev->
                    center = zoomManager.canvasCssPx2space(ev.nativeEvent.offsetX)
                    didChange = true
                }
            }
        } else if (dist == null) {
            div {
                key = "setUncertainty"
                css {
                    val pxCenter = zoomManager.space2canvasCssPx(center!!)
                    if (pxCenter < props.elementWidth / 2.0)
                        paddingLeft = (pxCenter + 20.0).px
                    else
                        paddingRight = (props.elementWidth - pxCenter + 20.0).px
                    fontFamily = FontFamily.sansSerif
                    fontWeight = integer(600)
                    fontSize = 15.px
                    lineHeight = 18.px
                    display = Display.flex
                    alignItems = AlignItems.center
                    textAlign = TextAlign.center
                    justifyContent = JustifyContent.center
                    color = Color("#6319FF")
                    flexDirection = FlexDirection.column
                    height = 100.pct
                    cursor = Cursor.default
                }
                div {
                    +"Tap here to set uncertainty"// TODO choose "tap"/"click" based on device
                    css {
                        paddingLeft = 10.px
                        paddingRight = 10.px
                    }
                }
                onClick = { ev->
                    // set ciWidth so that a blue thumb ends up where you clicked
                    val desiredCIBoundary = zoomManager.canvasCssPx2space(ev.nativeEvent.clientX.toDouble() - props.element.getBoundingClientRect().left)
                    val desiredCIRadius = abs(center!! - desiredCIBoundary)
                    val newCIWidth = if (center!! - desiredCIRadius < space.min)
                        desiredCIBoundary - space.min
                    else if (center!! + desiredCIRadius > space.max)
                        space.max - desiredCIBoundary
                    else
                        2 * desiredCIRadius
                    ciWidth = newCIWidth
                    didChange = true
                    update(center!!, newCIWidth, true)
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
    useEffect(propDist?.pseudoMean, propDist?.pseudoStdev) { previewDist = propDist }
    Stack {
        css {
            overflowX = Overflow.hidden
        }
        NumericPredGraph {
            this.space = props.space as NumericSpace
            this.dist = previewDist
            onZoomChange = { zoomParams = it }
        }
        NumericPredSlider {
            this.space = props.space
            this.zoomParams = zoomParams
            this.dist = props.dist
            this.onChange = {
                previewDist = it as TruncatedNormalDistribution
                props.onChange?.invoke(it)
            }
            this.onCommit = props.onCommit
        }

    }
}