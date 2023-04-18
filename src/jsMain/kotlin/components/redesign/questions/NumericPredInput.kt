package components.redesign.questions

import components.redesign.basic.*
import csstype.*
import dom.html.HTMLDivElement
import emotion.css.*
import emotion.react.*
import hooks.usePureClick
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.distributions.*
import tools.confido.spaces.*
import tools.confido.utils.*
import utils.panzoom1d.PZParams
import utils.panzoom1d.PZState
import kotlin.math.*


external interface NumericPredInputProps : PredictionInputProps {
}

external interface NumericPredSliderProps : NumericPredInputProps, PropsWithElementSize {
    var zoomState: PZState
    var marks: List<Double>
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

val NumericPredSlider = elementSizeWrapper(FC<NumericPredSliderProps>("NumericPredSlider") { props->
    val space = props.space as NumericSpace
    val zoomState = props.zoomState
    val propDist = props.dist as? TruncatedNormalDistribution
    val disabled = props.disabled ?: false
    var center by useState(propDist?.pseudoMean)
    var ciWidth by useState(propDist?.confidenceInterval(0.8)?.size)
    var dragging by useState(false)
    val ciRadius = ciWidth?.let { it / 2.0 }
    val minCIRadius = props.zoomState.paperToContent(20.0) // do not allow the thumbs to overlap too much
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
    fun update(newCenter: Double, newCIWidth: Double, isCommit: Boolean) {
        val newDist = findDistribution(space, newCenter, newCIWidth)
        props.onChange?.invoke(newDist)
        if (isCommit)
            props.onCommit?.invoke(newDist)
    }
    val createEstimateRE = usePureClick<HTMLDivElement> { ev->
        if (center == null) {
            center = props.zoomState.viewportToContent(ev.offsetX)
            didChange = true
        }
    }
    val setUncertaintyRE = usePureClick<HTMLDivElement> { ev->
        // set ciWidth so that a blue thumb ends up where you clicked
        console.log("zs=${props.zoomState} center=${center}")
        if(dist == null) {
            center?.let {center->
                val desiredCIBoundary =
                    props.zoomState.viewportToContent(ev.clientX.toDouble() - props.element.getBoundingClientRect().left)
                val desiredCIRadius = abs(center - desiredCIBoundary)
                val newCIWidth = if (center - desiredCIRadius < space.min)
                    desiredCIBoundary - space.min
                else if (center + desiredCIRadius > space.max)
                    space.max - desiredCIBoundary
                else
                    2 * desiredCIRadius
                ciWidth = newCIWidth
                didChange = true
                update(center, newCIWidth, true)
            }
        }
    }
    //useEffect(dist?.pseudoMean, dist?.pseudoStdev, didChange) {
    //    if (didChange) dist?.let { props.onChange?.invoke(dist) }
    //}
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
            }
        if (dist != null)
            SliderThumb{
                key = "thumb_left"
                this.containerElement = props.element
                this.zoomState = zoomState
                this.formatSignpost = { v -> space.formatValue(v) }
                kind = ThumbKind.Left
                pos = ci!!.start
                this.disabled = disabled
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
                this.zoomState = zoomState
                this.formatSignpost = { v -> space.formatValue(v) }
                kind = ThumbKind.Center
                pos = center!!
                this.disabled = disabled
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
                this.zoomState = zoomState
                this.formatSignpost = { v -> space.formatValue(v) }
                kind = ThumbKind.Right
                pos = ci!!.endInclusive
                this.disabled = disabled
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
        if (!disabled) {
            if (center == null) {
                div {
                    key = "setCenter"
                    css {
                        fontFamily = sansSerif
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
                    ref = createEstimateRE
                }
            } else if (dist == null) {
                div {
                    key = "setUncertainty"
                    css {
                        val pxCenter = props.zoomState.contentToViewport(center!!)
                        if (pxCenter < props.elementWidth / 2.0)
                            paddingLeft = (pxCenter + 20.0).px
                        else
                            paddingRight = (props.elementWidth - pxCenter + 20.0).px
                        fontFamily = sansSerif
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
                    ref = setUncertaintyRE
                }

            }
        }
    }
}, ClassName {
    // overflowX = Overflow.hidden // FIXME apparently, this does not work
    // overflowY = Overflow.visible
})


val NumericPredInput = FC<NumericPredInputProps>("NumericPredInput") { props->
    var zoomState by useState<PZState>()
    var marks by useState(emptyList<Double>())
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
            onZoomChange = { newZoomState, newMarks ->
                console.log("OZC")
                zoomState = newZoomState; marks = newMarks; }
        }
        if (zoomState != null)
        NumericPredSlider {
            this.disabled = props.disabled
            this.space = props.space
            this.marks = marks
            this.zoomState = zoomState!!
            this.dist = props.dist
            this.onChange = {
                previewDist = it as TruncatedNormalDistribution
                props.onChange?.invoke(it)
            }
            this.onCommit = props.onCommit
        }
    }
}