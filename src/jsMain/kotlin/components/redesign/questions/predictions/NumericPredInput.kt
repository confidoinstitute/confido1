package components.redesign.questions.predictions

import components.redesign.AsymmetricGaussIcon
import components.redesign.SymmetricGaussIcon
import components.redesign.basic.*
import components.redesign.forms.Switch
import components.redesign.forms.SwitchProps
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
    var asymmetric: Boolean?
}

fun findDistribution(space: NumericSpace, center: Double, ciWidth: Double): TruncatedNormalDistribution {
    val pseudoStdev = binarySearch(0.0..4*ciWidth, ciWidth, 30) {
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
    //     diverges, returns astronomically large stdev and creates weird artifacts
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
})


val NumericPredSliderAsym = elementSizeWrapper(FC<NumericPredSliderProps>("NumericPredSlider") { props->
    val layoutMode = useContext(LayoutModeContext)
    val space = props.space as NumericSpace
    val zoomState = props.zoomState
    val propDist = props.dist!!.let { dist ->
        when (dist) {
            is TruncatedSplitNormalDistribution -> dist
            // XXX is this the conversion we want?
            is TruncatedNormalDistribution -> TruncatedSplitNormalDistribution(dist.space, dist.pseudoMean, dist.pseudoStdev, dist.pseudoStdev)
            else -> return@FC
        }
    }
    val disabled = props.disabled ?: false
    var center by useState(propDist.center)
    var ci by useState(List2(propDist.icdf(0.1).coerceIn(space.min, center),
                            propDist.icdf(0.9).coerceIn(center, space.max)))
    val minCIRadius = props.zoomState.paperDistToContent(20.0) // do not allow the thumbs to overlap too much
    // For CIWidth -> 0.8 this converges to uniform distribution
    //     CIWidth > 0.8 there is no solution (the distribution would need to be convex) and distribution search
    //     diverges, returns astronomically large stdev and creates weird artivacts
    var dragging by useState(false)
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER
    var didChange by useState(false)
    useEffectOnce { console.log("SLIDER INIT") }
    useEffect(propDist.center, propDist.s1, propDist.s2) {
        console.log("new propdist ${propDist.center} ${propDist.s1} ${propDist.s2}")
        propDist.let {
            center = propDist.center.coerceIn(space.range)
            ci = List2(propDist.icdf(0.1).coerceIn(space.min, center),
                         propDist.icdf(0.9).coerceIn(center, space.max))
            didChange = false
        }
    }
    val dist = useMemo(space, center, ci.e1, ci.e2) {
        TruncatedSplitNormalDistribution.findByCI(space, center, 0.1, ci.e1, 0.9, ci.e2)
    }
    fun update(newCenter: Double, newLeft: Double, newRight: Double, isCommit: Boolean): TruncatedSplitNormalDistribution {
        println("upd0")
        val newDist = TruncatedSplitNormalDistribution.findByCI(space, newCenter,
            0.1, newLeft.coerceIn(space.min, newCenter),
            0.9, newRight.coerceIn(newCenter, space.max))
        println("upd1")
        props.onChange?.invoke(newDist)
        println("upd2")
        if (isCommit)
            props.onCommit?.invoke(newDist)
        println("upd3")
        return newDist
    }
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
        SliderTrack {
            +props
        }
        fun sideThumb(side: Int) {
            val sideName = listOf("left", "right")[side]
            val quantile = listOf(0.1, 0.9)[side]
            SliderThumb {
                key = "thumb_$sideName"
                this.containerElement = props.element
                this.zoomState = zoomState
                this.formatSignpost = { v -> space.formatValue(v) }
                kind = listOf(ThumbKind.Left, ThumbKind.Right)[side]
                pos = ci[side]
                this.disabled = disabled
                onDrag = { pos, isCommit ->
                    console.log("drag $sideName")
                    val effectivePos = pos.coerceIn(if (side == 0) space.min..(center - minCIRadius)
                                                    else (center+minCIRadius)..space.max)
                    console.log("center=$center ciOrig=${ci} pos=$pos")
                    val newCi = ci.replace(side, effectivePos)
                    ci = newCi
                    didChange = true
                    val newDist = update(center, newCi.e1, newCi.e2, isCommit)
                    val achievedPos = newDist.icdf(quantile)
                    if (abs(achievedPos - effectivePos) / abs(space.size) > 1e-5)
                        ci = ci.replace(side, achievedPos)
                }
                onDragStart = { dragging = true }
                onDragEnd = {
                    dragging = false
                }
            }
        }
        // We need to keep the order of the nodes left -> center -> right in order to get correct tab navigation.
        sideThumb(0)
        SliderThumb{
            key = "thumb_center"
            this.containerElement = props.element
            this.zoomState = zoomState
            this.formatSignpost = { v -> space.formatValue(v) }
            kind = ThumbKind.Center
            pos = center
            this.disabled = disabled
            onDrag = { pos, isCommit ->
                val delta = pos - center
                val newLeft = (ci.e1 + delta).coerceIn(space.min, pos)
                val newRight = (ci.e2 + delta).coerceIn(pos, space.max)
                println("center drag $pos $center $delta $newLeft $newRight")
                center = pos
                ci = List2(newLeft, newRight)
                didChange = true
                update(pos, newLeft, newRight, isCommit)
            }
            onDragStart = { dragging = true }
            onDragEnd = { dragging = false }
        }
        sideThumb(1)
    }
})


val NumericPredInput = FC<NumericPredInputProps>("NumericPredInput") { props->
    var zoomState by useState<PZState>()
    var marks by useState(emptyList<Double>())
    val propDist = props.dist as? ContinuousProbabilityDistribution?
    var pushDownDist by useState(propDist)
    useEffect(propDist?.identify()) { pushDownDist = propDist }
    var previewDist by useState(propDist)
    var didSetCenter by useState(false)
    val simulateClickRef = useRef<(Double)->Unit>()
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER
    var asymmetric by useState(propDist is TruncatedSplitNormalDistribution)
    val space = props.space as NumericSpace
    fun setAsymmetric(newAsym: Boolean) {
        previewDist?.let {
            val newDist = if (newAsym && it is TruncatedNormalDistribution)
                TruncatedSplitNormalDistribution(space, it.pseudoMean, it.pseudoStdev, it.pseudoStdev)
            else if ((!newAsym) && it is TruncatedSplitNormalDistribution)
                TruncatedNormalDistribution(space, it.center, (it.s1 + it.s2) / 2.0) //FIXME how to set this
            else return@let
            previewDist = newDist
            pushDownDist = newDist
        }
        asymmetric = newAsym
    }
    useEffect(propDist?.identify()) { previewDist = propDist; asymmetric = propDist is TruncatedSplitNormalDistribution }
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
            div {
                css {
                    position = Position.absolute
                    left = 8.px
                    top = 8.px
                    zIndex = integer(10)
                }
                // To make it a bit simpler, first only offer to create symmetric distribution and after it is created,
                // offer the asymmetry switch
                if (pushDownDist != null || previewDist != null)
                SymmetrySwitch {
                    checked = asymmetric
                    onChange = { e-> setAsymmetric(e.target.checked) }
                }
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
        val comp = if (asymmetric) NumericPredSliderAsym else NumericPredSlider
        if (zoomState != null)
        comp {
            this.disabled = props.disabled
            this.space = props.space
            this.marks = marks
            this.zoomState = zoomState!!
            this.dist = pushDownDist
            this.onChange = {
                previewDist = it as ContinuousProbabilityDistribution
                props.onChange?.invoke(it)
            }
            this.onCenterChange = { didSetCenter = true }
            this.onCommit = props.onCommit
            this.question = props.question
            this.simulateClickRef = simulateClickRef
        }
    }
}