package components.redesign.questions.predictions

import components.redesign.basic.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.PredictionOverlay
import csstype.*
import dom.html.HTMLDivElement
import emotion.css.*
import emotion.react.*
import hooks.usePureClick
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.distributions.*
import tools.confido.question.PredictionTerminology
import tools.confido.spaces.*
import tools.confido.utils.*
import utils.panzoom1d.PZParams
import utils.panzoom1d.PZState
import kotlin.math.*


external interface NumericPredInputProps : PredictionInputProps {
}

typealias SimulateClickRef = react.MutableRefObject<(Double)->Unit>
external interface NumericPredSliderProps : NumericPredInputProps, PropsWithElementSize {
    var zoomState: PZState
    var marks: List<Double>
    // Fired when only a center is set up but not yet a full distribution
    var onCenterChange: ((Double)->Unit)?
    var simulateClickRef: SimulateClickRef?
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
    val layoutMode = useContext(LayoutModeContext)
    val space = props.space as NumericSpace
    val zoomState = props.zoomState
    val propDist = props.dist as? TruncatedNormalDistribution
    val disabled = props.disabled ?: false
    var center by useState(propDist?.pseudoMean)
    // For CIWidth -> 0.8 this converges to uniform distribution
    //     CIWidth > 0.8 there is no solution (the distribution would need to be convex) and distribution search
    //     diverges, returns astronomically large stdev and creates weird artivacts
    val maxCIWidth = 0.798 * space.size
    var ciWidth by useState(propDist?.confidenceInterval(0.8)?.size?.coerceIn(0.0..maxCIWidth))
    var dragging by useState(false)
    val ciRadius = ciWidth?.let { it / 2.0 }
    val minCIRadius = props.zoomState.paperDistToContent(20.0) // do not allow the thumbs to overlap too much
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER
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
        val newDist = findDistribution(space, newCenter, newCIWidth.coerceIn(0.0..maxCIWidth))
        props.onChange?.invoke(newDist)
        if (isCommit)
            props.onCommit?.invoke(newDist)
    }
    val createEstimateRE = usePureClick<HTMLDivElement> { ev->
        if (center == null) {
            val newCenter = props.zoomState.viewportToContent(ev.offsetX)
            props.onCenterChange?.invoke(newCenter)
            center = newCenter
            didChange = true
        }
    }
    fun setCIBoundary(desiredCIBoundary: Double) {
        if (dist == null) {
            center?.let { center ->
                val desiredCIRadius = abs(center - desiredCIBoundary)
                val newCIWidth = (if (center - desiredCIRadius < space.min)
                    desiredCIBoundary - space.min
                else if (center + desiredCIRadius > space.max)
                    space.max - desiredCIBoundary
                else
                    2 * desiredCIRadius).coerceIn(0.0..maxCIWidth)
                ciWidth = newCIWidth
                didChange = true
                update(center, newCIWidth, true)
            }
        }
    }
    val setUncertaintyRE = usePureClick<HTMLDivElement> { ev->
        // set ciWidth so that a blue thumb ends up where you clicked
        console.log("zs=${props.zoomState} center=${center}")
        val desiredCIBoundary =
            props.zoomState.viewportToContent(ev.clientX.toDouble() - props.element.getBoundingClientRect().left)
        setCIBoundary(desiredCIBoundary)
    }
    fun simulateClick(spaceX: Double) {
        if (center == null) center = spaceX
        else if (dist == null) setCIBoundary(spaceX)
    }
    props.simulateClickRef?.let { it.current = ::simulateClick }
    //useEffect(dist?.pseudoMean, dist?.pseudoStdev, didChange) {
    //    if (didChange) dist?.let { props.onChange?.invoke(dist) }
    //}
    //useEffect(dist?.pseudoMean, dist?.pseudoStdev, dragging) {
    //    if (!dragging) dist?.let { props.onCommit?.invoke(dist) }
    //}
    val interactVerb = if (layoutMode >= LayoutMode.TABLET) { "Click" } else { "Tap" }

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
                        val newCIWidth = (if (center + naturalRadius > space.max) {
                             space.max - effectivePos
                        } else {
                             2 * naturalRadius
                        }).coerceIn(0.0..maxCIWidth)
                        console.log("pos=$pos effectivePos=$effectivePos center=$center minCIradius=$minCIRadius newCIWidth=$newCIWidth")
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
                    else props.onCenterChange?.invoke(pos)
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
                        val newCIWidth = (if (center - naturalRadius < space.min) {
                            effectivePos - space.min
                        } else {
                             2 * naturalRadius
                        }).coerceIn(0.0..maxCIWidth)
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
                    +"$interactVerb here to create ${predictionTerminology.aTerm}"
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
                        +"$interactVerb here to set uncertainty"
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
    var didSetCenter by useState(false)
    val simulateClickRef = useRef<(Double)->Unit>()
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER
    useEffect(propDist?.pseudoMean, propDist?.pseudoStdev) { previewDist = propDist }
    Stack {
        css {
            overflowX = Overflow.hidden
        }
        div {
            css { position = Position.relative }
            NumericPredGraph {
                this.space = props.space as NumericSpace
                this.dist = previewDist
                this.question = props.question
                this.resolution = props.resolution as NumericValue?
                this.isInput = true
                this.isGroup = false
                this.dimLines = (previewDist == null)
                this.onGraphClick = { spaceX, relY ->
                    console.log("OGC $spaceX $relY ${simulateClickRef.current}")
                    if (relY >= 2.0/3.0)
                    simulateClickRef.current?.invoke(spaceX)
                }
                onZoomChange = { newZoomState, newMarks ->
                    console.log("OZC")
                    zoomState = newZoomState; marks = newMarks; }
            }
            // Hide the overlay when a center is set, otherwise it overlays the center signpost if you try to move
            // the center thumb before setting uncertainty.
            if (previewDist == null && !didSetCenter)
            PredictionOverlay {
                // Cannot use dimBackground because it would dim the axis labels, which are important when creating
                // a prediction. Instead, we use NumericPredGraphProps.dimLines to dim the vertical lines only but
                // not the labels.
                dimBackground = false
                +"Click below to create ${predictionTerminology.aTerm}. You can also zoom the graph using two fingers or the mouse wheel."
            }
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
            this.onCenterChange = { didSetCenter = true }
            this.onCommit = props.onCommit
            this.question = props.question
            this.simulateClickRef = simulateClickRef
        }
    }
}