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
import tools.confido.question.Question
import tools.confido.spaces.*
import tools.confido.utils.*
import utils.panzoom1d.PZParams
import utils.panzoom1d.PZState
import kotlin.math.*


external interface NumericPredInputProps : PredictionInputProps {
}

typealias SimulateClickRef = react.MutableRefObject<(Double)->Unit>
external interface NumericPredSliderProps :  PropsWithElementSize {
    var space: NumericSpace
    var disabled: Boolean?
    var question: Question?
    var zoomState: PZState
    var marks: List<Double>
    // Fired when only a center is set up but not yet a full distribution
    var onSpecChange: ((NormalishDistSpec, Boolean) -> Unit)?
    var simulateClickRef: SimulateClickRef?
    var asymmetric: Boolean?
    var spec: NormalishDistSpec?
}


val NumericPredSlider = elementSizeWrapper(FC<NumericPredSliderProps>("NumericPredSlider") { props->
    val layoutMode = useContext(LayoutModeContext)
    val space = props.space
    val zoomState = props.zoomState
    val spec = props.spec ?: NumericDistSpecSym(space, null, null)
    val disabled = props.disabled ?: false
    // For CIWidth -> 0.8 this converges to uniform distribution
    //     CIWidth > 0.8 there is no solution (the distribution would need to be convex) and distribution search
    //     diverges, returns astronomically large stdev and creates weird artifacts
    val minCIRadiusThumb = props.zoomState.paperDistToContent(20.0) // do not allow the thumbs to overlap too much
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER
    var didChange by useState(false)
    useEffectOnce { console.log("SLIDER INIT") }
    fun updateSpec(isCommit: Boolean, fn: (NormalishDistSpec) -> NormalishDistSpec) {
        val newSpec = fn(spec)
        props.onSpecChange?.invoke(newSpec, isCommit)
    }
    val createEstimateRE = usePureClick<HTMLDivElement> { ev->
        if (spec.center == null) {
            val newCenter = props.zoomState.viewportToContent(ev.offsetX)
            updateSpec(true) { NumericDistSpecSym(space, newCenter, null) }
        }
    }
    val setUncertaintyRE = usePureClick<HTMLDivElement> { ev->
        // set ciWidth so that a blue thumb ends up where you clicked
        console.log("zs=${props.zoomState} center=${spec.center}")
        val desiredCIBoundary =
            props.zoomState.viewportToContent(ev.clientX.toDouble() - props.element.getBoundingClientRect().left)
        updateSpec(true) { it.setCiBoundary(desiredCIBoundary).coerceToRealistic() }
    }
    fun simulateClick(spaceX: Double) {
        if (spec.center == null) updateSpec(true) { it.setCenter(spaceX) }
        else if (spec.ci == null) updateSpec(true) { it.setCiBoundary(spaceX) }
    }
    props.simulateClickRef?.let { it.current = ::simulateClick }
    val interactVerb = if (layoutMode >= LayoutMode.TABLET) { "Click" } else { "Tap" }

    div {
        key = "sliderArea"
        css {
            height = 40.px
            minHeight = 40.px
            flexShrink = number(0.0)
            position = Position.relative
            // overflowX = Overflow.hidden // FIXME apparently, this does not work
            // overflowY = Overflow.visible
        }
        if (spec.ci != null)
            SliderTrack {
                +props
            }

        fun sideThumb(side: Int) {
            val sideName = listOf("left", "right")[side]
            multiletNotNull(spec.ci, spec.center) { ci, center ->
                SliderThumb {
                    key = "thumb_$sideName"
                    this.containerElement = props.element
                    this.zoomState = zoomState
                    this.formatSignpost = { v -> space.formatValue(v) }
                    kind = listOf(ThumbKind.Left, ThumbKind.Right)[side]
                    pos = ci[side]
                    val coerceRangeThumb = if (side == 0) space.min..maxOf(center - minCIRadiusThumb, space.min)
                    else minOf(space.max, center + minCIRadiusThumb)..space.max
                    thumbPos = pos.coerceIn(coerceRangeThumb)
                    this.disabled = disabled
                    onDrag = { pos, isCommit ->
                        updateSpec(isCommit) { it.setCiBoundary(pos, side).coerceToRealistic() }
                    }
                }
            }
        }
        if (spec.ci != null) sideThumb(0)
        if (spec.center != null)
            SliderThumb{
                key = "thumb_center"
                this.containerElement = props.element
                this.zoomState = zoomState
                this.formatSignpost = { v -> space.formatValue(v) }
                kind = ThumbKind.Center
                pos = spec.center!!
                this.disabled = disabled
                onDrag = { pos, isCommit ->
                    updateSpec(isCommit) { it.setCenter(pos)  }
                    didChange = true
                }
            }
        if (spec.ci != null) sideThumb(1)
        if (!disabled) {
            if (spec.center == null) {
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
            } else if (spec.ci == null) {
                div {
                    key = "setUncertainty"
                    css {
                        val pxCenter = props.zoomState.contentToViewport(spec.center!!)
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


val NumericPredInput = FC<NumericPredInputProps>("NumericPredInput") { props->
    val space = props.space as NumericSpace
    var zoomState by useState<PZState>()
    var marks by useState(emptyList<Double>())
    val propDist = props.dist as? ContinuousProbabilityDistribution?
    var spec by useState(if (propDist == null) NumericDistSpecSym(space,null,null) else NormalishDistSpec.fromDist(propDist))
    useEffect(propDist?.identify())  {
        if (propDist != null && propDist != spec.dist)
            spec = NormalishDistSpec.fromDist(propDist)
    }
    val previewDist = spec.useDist()
    val didSetCenter = spec.center != null
    val simulateClickRef = useRef<(Double)->Unit>()
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER
    fun setAsymmetric(newAsym: Boolean) {
        spec = spec.setAsymmetric(newAsym)
    }
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
                if (spec.ci != null)
                SymmetrySwitch {
                    checked = spec.asymmetric
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
        if (zoomState != null)
        NumericPredSlider {
            this.disabled = props.disabled
            this.space = props.space as NumericSpace
            this.marks = marks
            this.zoomState = zoomState!!
            this.spec = spec
            this.onSpecChange = { newSpec, isCommit ->
                spec = newSpec
                spec.dist?.let { dist ->
                    props.onChange?.invoke(dist)
                    if (isCommit) props.onCommit?.invoke(dist)
                }
            }
            this.question = props.question
            this.simulateClickRef = simulateClickRef
        }
    }
}