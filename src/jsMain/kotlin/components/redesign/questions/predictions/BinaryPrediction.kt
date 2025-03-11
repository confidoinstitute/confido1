package components.redesign.questions.predictions

import browser.*
import components.redesign.ZoomIcon
import components.redesign.basic.*
import components.redesign.forms.ButtonUnstyled
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.*
import csstype.*
import dom.Node
import dom.events.*
import dom.html.*
import emotion.react.*
import hooks.*
import kotlinx.js.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.calibration.CalibrationEntry
import tools.confido.distributions.*
import tools.confido.question.ExtremeProbabilityMode
import tools.confido.question.PredictionTerminology
import tools.confido.spaces.*
import tools.confido.utils.List2
import tools.confido.utils.endpoints
import tools.confido.utils.formatPercent
import tools.confido.utils.toFixed
import utils.panzoom1d.PZParams
import utils.panzoom1d.PZState
import utils.panzoom1d.usePanZoom
import kotlin.math.*

external interface BinaryPredictionProps : Props, BasePredictionGraphProps {
    override var dist: BinaryDistribution?
    override var space: BinarySpace
    override var resolution: BinaryValue?
    var baseHeight: Length?
    var sliderZoom: PZState?
    var onSliderZoomChange: ((PZState)->Unit)?
}

fun ChildrenBuilder.proportionalCircle(text: String, color: Color, prob: Double?, size: Double = 145.0, leftText:Boolean=false) {
    // Base size from Figma is 145 px, scale accordingly
    val scalar = size / 145.0
    var wantAlt = false
    div {
        css {
            display = Display.flex
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            this.color = Color("#FFFFFF")
            fontFamily = sansSerif
            width = size.px
            height = size.px
            position = Position.relative
        }

        if (prob == null) {
            //div {
            //    css {
            //        borderRadius = 100.pct
            //        border = Border(1.px, LineStyle.solid, rgba(0,0,0,0.05))
            //    }
            //    style = jso {
            //        width = (sqrt(0.5) * size).px
            //        height = (sqrt(0.5) * size).px
            //    }
            //}
        } else {
            div {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    alignItems = AlignItems.center
                    justifyContent = JustifyContent.center
                    width = 145.px
                    height = 145.px
                    flexShrink = number(0.0)
                    borderRadius = 100.pct
                    backgroundColor = color
                }
                val scale = scalar*sqrt(prob)
                style = jso {
                    transform = scale(scalar*sqrt(prob))
                }
                val percent = prob * 100
                val (decimals, fontSize) = if (percent <= 1 || percent >= 99 && percent < 100) {
                    2 to 38.0
                    //} else if (percent <= 10 || percent >= 90 && percent < 100) {
                    //    1 to 44.0
                } else {
                    0 to 50.0
                }
                val effectiveFontSize = scale*fontSize
                // When the font is too small, hide the text
                if (effectiveFontSize >= 10.0 || leftText) {
                    div {
                        css {
                            fontWeight = integer(700)
                            this.fontSize = fontSize.px
                            lineHeight = 60.px
                        }
                        +"${percent.toFixed(decimals)}%"
                    }
                    div {
                        css {
                            fontWeight = integer(600)
                            this.fontSize = 30.px
                            lineHeight = 35.px
                        }
                        +text
                    }
                } else {
                    div {
                        css {
                            position = Position.absolute
                            top = 50.pct
                            transform = translatey(-50.pct)
                            if (leftText) right = 0.px
                            else left = 100.pct
                            this.color = color
                            marginLeft = (5 / scale).px
                        }
                        Stack {
                            div {
                                css { this.fontSize = (20/scale).px }
                                +"${percent.toFixed(decimals)}%"

                            }
                            div {
                                css { this.fontSize = (16/scale).px }
                                +text
                            }
                        }
                    }
                }
            }
        }

    }
}

external interface  BinaryZoomButtonProps : PropsWithClassName {
    var curZoom: PZState
    var onZoomChange: ((PZState) -> Unit)?
}

val BinaryZoomButton = FC<BinaryZoomButtonProps> { props->
    var expanded by useState(false)
        Stack {
            direction = FlexDirection.row
            tabIndex = 0
            onBlur = { ev->
                // Auto close if focus gets outside out subtree
                // (check is needed as clicking one of the individual preset buttons focuses it causing
                //  the toplevel element to lose focus)
                if (ev.relatedTarget == null || !ev.currentTarget.contains(ev.relatedTarget as Node)) expanded = false
            }
            css {
                alignItems = AlignItems.center
                borderRadius = 5.px
                height = 30.px
                border = Border(1.px, LineStyle.solid, MainPalette.primary.color)
                background = NamedColor.white
                position = Position.relative
            }
            GraphButton {
                ZoomIcon {
                    css { transform = scalex(-1) }
                }
                onClick = { expanded = !expanded }
            }
            div {
                css {
                    fontSize = 12.px
                    color = NamedColor.black
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    overflow = Overflow.hidden
                    maxWidth = if (expanded) 300.px else if (props.curZoom.zoom > 1.0) 80.px else 0.px

                    transition = Transition(
                        property = PropertyName.maxWidth,
                        duration = 200.ms,
                        timingFunction = TransitionTimingFunction.easeInOut
                    )
                }
                if (expanded) {
                    EXTREME_PRESETS.forEach { (name, preset)->
                        val zoomState = props.curZoom
                        val paperCenter = zoomState.copy(zoom=preset.zoom).contentToPaper(preset.center)
                        val viewportCenter = props.curZoom.params.viewportWidth / 2
                        val pan = paperCenter - viewportCenter
                        val newState = PZState(zoomState.params, preset.zoom, pan)

                        ButtonUnstyled {
                            css {
                                padding = Padding(2.px, 5.px)
                                whiteSpace = WhiteSpace.nowrap
                                if (newState == props.curZoom)
                                    fontWeight = integer(700)
                            }
                            onClick = {
                                props.onZoomChange?.invoke(
                                    newState
                                )
                                expanded = false
                            }
                            +name
                        }
                    }
                } else {
                    if (props.curZoom.zoom > 1.0)
                    ButtonUnstyled {
                        css {
                            padding = Padding(2.px, 5.px)
                            whiteSpace = WhiteSpace.nowrap
                        }
                        onClick = {expanded=true}
                        +(props.curZoom.visibleContentRange.endpoints.joinToString("-") {
                            it.toFixed(0)
                        }+"%")
                    }
                }
            }
        }
}

val yesGreen = Color("#00CC2E")
val noRed = Color("#FF5555")
val binaryColors = List2(noRed, yesGreen)
val binaryNames = List2("No","Yes")
val BinaryPrediction = FC<BinaryPredictionProps> { props ->
    val realSize = useElementSize<HTMLElement>()

    val baseHeight = props.baseHeight ?: 145.px
    val circleSize: Double = min(realSize.height, realSize.width / 2)

    Stack {
        ref = combineRefs(realSize.ref.unsafeCast<MutableRefObject<HTMLElement>>(), props.ref.unsafeCast<MutableRefObject<HTMLElement>>())
        direction = FlexDirection.row
        css(override=props.className) {
            width = 100.pct
            height = baseHeight
            justifyContent = JustifyContent.center
            alignItems = AlignItems.center
            position = Position.relative
        }
        val noColor = if (props.resolution?.value == true) {
            Color("#BBBBBB")
        } else {
            noRed
        }
        val yesColor = if (props.resolution?.value == false) {
            Color("#BBBBBB")
        } else {
            yesGreen
        }
        proportionalCircle("No", noColor, props.dist?.yesProb?.let { 1 - it }, size = circleSize, leftText=true)
        proportionalCircle("Yes", yesColor, props.dist?.yesProb, size = circleSize)
        if (props.interactive ?: true) {
            props.sliderZoom?.let { zoom ->
                props.onSliderZoomChange?.let { onZoomChange->

                    GraphButtonContainer {
                        side = Side.LEFT
                        BinaryZoomButton {
                            curZoom = zoom
                            this.onZoomChange = onZoomChange
                        }
                    }
                }
            }
            GraphButtons {
                +props
                if (props.question != null) {
                    onHistogramClick = props.onHistogramButtonClick
                }
            }
        }
    }
}

data class ZoomPreset(
    val zoom: Double,
    val center: Double, // content coordinates
)

val EXTREME_PRESETS =
        mapOf(
            "0-1%" to ZoomPreset(100.0, 0.5),
            "0-10%" to ZoomPreset(10.0, 5.0),
            "0-100%" to ZoomPreset(1.0, 50.0),
            "90-100%" to ZoomPreset(10.0, 95.0),
            "99-100%" to ZoomPreset(100.0, 99.5)
        )

external interface RangeSelectorProps : PropsWithClassName {
    var zoomState: PZState
    var elementWidth: Double
    var onSelectRange: (ZoomPreset) -> Unit
}

val RangeSelector = FC<RangeSelectorProps> { props ->
    val presets = EXTREME_PRESETS
    // Prevent creating estimate when clicking on zoom
    val pointerRE = useEventListener<HTMLElement>("pointerdown") { it.stopPropagation() }

    Stack {
        direction = FlexDirection.row
        ref = pointerRE
        css(override=props.className) {
            marginTop = 8.px
            marginBottom = 8.px
            alignItems = AlignItems.baseline
        }
        span {
            css {
                color = Color("rgba(0,0,0,0.3)")
                fontWeight = integer(600)
            }
            +"Zoom: "

        }
        Stack {
            direction = FlexDirection.row
            css {
                alignItems = AlignItems.baseline
                border = Border(1.px, LineStyle.solid, Color("rgba(0,0,0,0.3)"))
                borderRadius = 5.px
                fontSize = 80.pct
            }
            presets.forEach { (label, preset) ->
                val isActive = abs(props.zoomState.zoom - preset.zoom) < 0.001 &&
                        abs(props.zoomState.viewportToContent(props.elementWidth / 2) - preset.center) < preset.zoom * 0.01
                div {
                    css {
                        cursor = Cursor.pointer
                        color = if (isActive) NamedColor.black else Color("#666")
                        fontWeight = if (isActive) integer(600) else integer(400)
                        padding = 4.px
                        if (preset.zoom == 1.0) {
                            borderLeft =  Border(1.px, LineStyle.solid, Color("rgba(0,0,0,0.3)"))
                            borderRight =  Border(1.px, LineStyle.solid, Color("rgba(0,0,0,0.3)"))
                        }
                    }
                    onClick = { props.onSelectRange(preset) }
                    +label
                }
            }
        }
    }
}

external interface BinaryPredSliderProps : PredictionInputProps, PropsWithElementSize {
    var onZoomChange: ((PZState)->Unit)?
    var zoom: PZState?
}
val BIN_PRED_SPACE = NumericSpace(0.0, 100.0, unit="%")
val BinaryPredSlider = elementSizeWrapper(FC<BinaryPredSliderProps> { props->
    val layoutMode = useContext(LayoutModeContext)
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER

    val maxZoom = 100.0
    val zoomParams = PZParams(viewportWidth = props.elementWidth, contentDomain = 0.0..100.0, sidePad = SIDE_PAD, maxZoom = maxZoom)
    val (pzRef, zoomState, ctl) = usePanZoom<HTMLElement >(zoomParams, initialState = props.zoom ?: PZState(zoomParams))
    useEffect(zoomState.zoom, zoomState.pan) { props.onZoomChange?.invoke(zoomState)}
    useEffectNotFirst(props.zoom?.zoom, props.zoom?.pan) {
        console.log("propzoom change")
        props.zoom?.let { newZoom -> ctl.state = newZoom }
    }

    fun formatPercentage(value: Double): String {
        val decimals = when {
            zoomState.zoom >= 100.0 -> 1
            else -> 0
        }
        return "${value.toFixed(decimals)}%"
    }
    val propProb = (props.dist as? BinaryDistribution)?.yesProb
    var yesProb by useState(propProb)
    var shouldAutoFocus by useState(false)
    val disabled = props.disabled?:false
    useEffect(propProb) {
        propProb?.let { yesProb = propProb }
        shouldAutoFocus = false // Reset when prop changes
    }
    fun update(newProb: Double, isCommit: Boolean) {
        yesProb = newProb
        props.onChange?.invoke(BinaryDistribution(newProb), isCommit)
    }
    val clickRE = usePureClick<HTMLElement> { ev->
        if (yesProb == null) {
            val rect = (ev.currentTarget as HTMLElement).getBoundingClientRect()
            val x = ev.clientX - rect.left
            val newProb = zoomState.viewportToContent(x) / 100.0
            shouldAutoFocus = true
            update(newProb, true)
        }
    }
    val interactVerb = if (layoutMode >= LayoutMode.TABLET) { "Click" } else { "Tap" }

    Stack {

        ref = combineRefs(clickRE, pzRef)
        val marks = useMemo(zoomState.paperWidth) {
            markSpacing(zoomState.paperWidth, 0.0, 100.0) { v: Number -> formatPercentage(v.toDouble()) }
        }
        val filteredMarks = marks.filter { it in zoomState.visibleContentRange }
        console.log("M: ${marks.toTypedArray()} FM: ${filteredMarks.toTypedArray() } ")

        div {
            key="percent_labels"
            css {
                height = 16.px
                flexGrow = number(0.0)
                flexShrink = number(0.0)
                fontSize = 10.px
                color = Color("rgba(0,0,0,30%)")
                lineHeight = 12.1.px
                position = Position.relative
                fontFamily = sansSerif
                fontWeight = integer(600)
            }
            filteredMarks.forEachIndexed { idx, value ->
                div {
                    style = jso {
                        left = zoomState.contentToViewport(value).px
                        top = 50.pct
                    }
                    css {
                        val xtrans = when(idx) {
                            0 -> max((-50).pct, (-SIDE_PAD).px)
                            else -> (-50).pct
                        }
                        transform = translate(xtrans, (-50).pct)
                        position = Position.absolute
                    }
                    +formatPercentage(value)
                }
            }
        }
        div {
            key = "sliderArea"
            css {
                height = 40.px
                minHeight = 40.px
                flexShrink = number(0.0)
                position = Position.relative
            }
            if (yesProb == null) {
                if (!disabled)
                    div {
                        key = "createEstimate"
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
                    }
            } else {
                SliderTrack {
                    key = "track"
                    this.marks = filteredMarks
                    this.zoomState = zoomState
                }

                SliderThumb{
                    key = "thumb_center"
                    this.containerElement = props.element
                    this.zoomState = zoomState
                    kind = ThumbKind.Center
                    pos = 100.0 * yesProb!!
                    onDrag = { pos, isCommit ->
                        if (!disabled)
                        update(pos/100.0, isCommit)
                    }
                    signpostEnabled = false
                    this.disabled = disabled
                    this.autoFocus = shouldAutoFocus
                }
            }

        }
        // Only show verbal labels at default zoom
        if (abs(zoomState.zoom - 1.0) < 0.001) {
            div {
                key="word_labels"
                css {
                    height = 16.px
                    flexGrow = number(0.0)
                    flexShrink = number(0.0)
                    fontSize = 10.px
                    color = Color("rgba(0,0,0,30%)")
                    lineHeight = 12.1.px
                    position = Position.relative
                    fontFamily = sansSerif
                    fontWeight = integer(600)
                }
                listOf(
                    0 to "No",
                    25 to "Improbable",
                    50 to "Even odds",
                    75 to "Probable",
                    100 to "Yes",
                ).forEachIndexed { idx, (value, text) ->
                    div {
                        style = jso {
                            left = zoomState.contentToViewport(value.toDouble()).px
                            top = 50.pct
                        }
                        css {
                            val xtrans = when(idx) {
                                0 -> max((-50).pct, (-SIDE_PAD).px)
                                else -> (-50).pct
                            }
                            transform = translate(xtrans, (-50).pct)
                            position = Position.absolute
                        }
                        +text
                    }
                }
            }
        }
        if(false)
        RangeSelector {
            css {
                alignSelf = AlignSelf.center
            }
            this.zoomState = zoomState
            this.elementWidth = props.elementWidth
            this.onSelectRange = { preset: ZoomPreset ->
                val paperCenter = zoomState.copy(zoom=preset.zoom).contentToPaper(preset.center)
                val viewportCenter = props.elementWidth / 2
                val pan = paperCenter - viewportCenter
                val newState = PZState(zoomState.params, preset.zoom, pan)
                ctl.state = newState
            }
        }
    }
})

external interface BinaryPredInputProps : PredictionInputProps {
}

val BinaryPredInput = FC<BinaryPredInputProps> { props->
    val propDist = (props.dist as? BinaryDistribution)
    var previewDist by useState(propDist)
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER
    useEffect(propDist?.yesProb) { previewDist = propDist }
    var sliderZoom by useState<PZState>()

    val realSize = useElementSize<HTMLElement>()
    val clickRE = usePureClick<HTMLElement> { ev->
        if (previewDist == null) {
            val elem = ev.currentTarget as HTMLElement
            val rect = elem.getBoundingClientRect()
            val width = rect.width
            val x = ev.clientX - rect.left
            val zoomParams = PZParams(viewportWidth = width, contentDomain = 0.0..100.0, sidePad = SIDE_PAD)
            val zoomState = sliderZoom ?: PZState(zoomParams)
            val pos = zoomState.viewportToContent(x)
            val newDist = BinaryDistribution(pos / 100.0)
            console.log("x: $x, pos: $pos, width: $width")
            previewDist = newDist
            props.onChange?.invoke(newDist, true)
        }

    }
    Stack {
        css {
            overflowX = Overflow.hidden
        }
        ref = clickRE
        div {
            css { position = Position.relative }
            BinaryPrediction {
                this.dist = previewDist
                this.question = props.question
                this.resolution = props.resolution as BinaryValue?
                this.isInput = true
                this.isGroup = false
                this.sliderZoom = sliderZoom
                this.onSliderZoomChange = { sliderZoom = it }
            }
            if (previewDist == null) {
                PredictionOverlay {
                    +"Click below to create ${predictionTerminology.aTerm}"
                    dimBackground = false
                }
            }
        }
        BinaryPredSlider {
            this.space = props.space
            this.dist = props.dist
            this.onChange = { newDist, isCommit->
                previewDist = (newDist as BinaryDistribution)
                props.onChange?.invoke(newDist, isCommit)
            }
            this.zoom = sliderZoom
            onZoomChange = { sliderZoom = it }
            this.disabled = props.disabled
        }
    }
}
