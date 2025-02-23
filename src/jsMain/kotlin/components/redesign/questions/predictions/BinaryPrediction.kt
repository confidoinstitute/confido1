package components.redesign.questions.predictions

import browser.*
import components.redesign.basic.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.*
import csstype.*
import dom.events.*
import dom.html.*
import emotion.react.*
import hooks.*
import kotlinx.js.*
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.calibration.CalibrationEntry
import tools.confido.distributions.*
import tools.confido.question.ExtremeProbabilityMode
import tools.confido.question.PredictionTerminology
import tools.confido.spaces.*
import tools.confido.utils.List2
import tools.confido.utils.toFixed
import utils.markSpacing
import utils.panzoom1d.PZParams
import utils.panzoom1d.PZState
import utils.panzoom1d.usePanZoom
import kotlin.math.*

external interface BinaryPredictionProps : Props, BasePredictionGraphProps {
    override var dist: BinaryDistribution?
    override var space: BinarySpace
    override var resolution: BinaryValue?
    var baseHeight: Length?
}

fun ChildrenBuilder.proportionalCircle(text: String, color: Color, prob: Double?, size: Double = 145.0) {
    // Base size from Figma is 145 px, scale accordingly
    val scalar = size / 145.0
    div {
        css {
            display = Display.flex
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            this.color = Color("#FFFFFF")
            fontFamily = sansSerif
            width = size.px
            height = size.px
        }

        if (prob == null) {
            div {
                css {
                    borderRadius = 100.pct
                    border = Border(1.px, LineStyle.solid, rgba(0,0,0,0.05))
                }
                style = jso {
                    width = (sqrt(0.5) * size).px
                    height = (sqrt(0.5) * size).px
                }
            }
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
                style = jso {
                    transform = scale(scalar*sqrt(prob))
                }
                // When the font is too small, hide the text
                if (scalar >= 0.3) {
                    div {
                        val percent = prob * 100
                        val (decimals, fontSize) = if (percent <= 1 || percent >= 99 && percent < 100) {
                            2 to 38.px
                        } else if (percent <= 10 || percent >= 90 && percent < 100) {
                            1 to 44.px
                        } else {
                            0 to 50.px
                        }
                        css {
                            fontWeight = integer(700)
                            this.fontSize = fontSize
                            lineHeight = 60.px
                        }
                        +"${percent.toFixed(decimals)}%"
                    }
                    div {
                        css {
                            fontWeight = integer(600)
                            fontSize = 30.px
                            lineHeight = 35.px
                        }
                        +text
                    }
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
        ref = realSize.ref
        direction = FlexDirection.row
        css {
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
        proportionalCircle("No", noColor, props.dist?.yesProb?.let { 1 - it }, size = circleSize)
        proportionalCircle("Yes", yesColor, props.dist?.yesProb, size = circleSize)
        if (props.interactive ?: true) {
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

val EXTREME_PRESETS = { mode: ExtremeProbabilityMode ->
    when (mode) {
        ExtremeProbabilityMode.NORMAL -> null
        ExtremeProbabilityMode.EXTREME_LOW -> mapOf(
            "0-100%" to ZoomPreset(1.0, 50.0),
            "0-10%" to ZoomPreset(10.0, 5.0),
            "0-1%" to ZoomPreset(100.0, 0.5)
        )
        ExtremeProbabilityMode.EXTREME_HIGH -> mapOf(
            "0-100%" to ZoomPreset(1.0, 50.0),
            "90-100%" to ZoomPreset(10.0, 95.0),
            "99-100%" to ZoomPreset(100.0, 99.5)
        )
    }
}

external interface RangeSelectorProps : PropsWithClassName {
    var extremeProbabilityMode: ExtremeProbabilityMode
    var zoomState: PZState
    var elementWidth: Double
    var onSelectRange: (ZoomPreset) -> Unit
}

val RangeSelector = FC<RangeSelectorProps> { props ->
    val presets = EXTREME_PRESETS(props.extremeProbabilityMode) ?: return@FC
    val centerOrigin : Transform = translate((-50).pct, (-50).pct)

    Stack {
        direction = FlexDirection.row
        css(override=props.className) {
            gap = 8.px
            marginTop = 8.px
        }
        +"Range: "
        presets.forEach { (label, preset) ->
            val isActive = abs(props.zoomState.zoom - preset.zoom) < 0.001 &&
                          abs(props.zoomState.viewportToContent(props.elementWidth / 2) - preset.center) < preset.zoom * 0.01
            div {
                css {
                    cursor = Cursor.pointer
                    color = if (isActive) Color("#6319FF") else Color("rgba(0,0,0,0.3)")
                    fontWeight = if (isActive) integer(600) else integer(400)
                }
                onClick = { props.onSelectRange(preset) }
                +label
            }
        }
    }
}

external interface BinaryPredSliderProps : PredictionInputProps, PropsWithElementSize {
    var extremeProbabilityMode: ExtremeProbabilityMode?
}
val BIN_PRED_SPACE = NumericSpace(0.0, 100.0, unit="%")
val BinaryPredSlider = elementSizeWrapper(FC<BinaryPredSliderProps> { props->
    val layoutMode = useContext(LayoutModeContext)
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER
    val extremeProbabilityMode = props.extremeProbabilityMode ?:  ExtremeProbabilityMode.NORMAL

    val maxZoom = if (extremeProbabilityMode == ExtremeProbabilityMode.NORMAL) 1.0 else 100.0
    val zoomParams = PZParams(viewportWidth = props.elementWidth, contentDomain = 0.0..100.0, sidePad = SIDE_PAD, maxZoom = maxZoom)
    val (pzRef, zoomState, ctl) = usePanZoom<HTMLElement >(zoomParams)

    fun formatPercentage(value: Double): String {
        val decimals = when {
            zoomState.zoom >= 100.0 -> 2 // Show 2 decimals at highest zoom (e.g. 0.45%)
            zoomState.zoom >= 10.0 -> 1  // Show 1 decimal at medium zoom (e.g. 4.5%)
            else -> 0                    // Show no decimals at default zoom (e.g. 45%)
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
        if (extremeProbabilityMode != ExtremeProbabilityMode.NORMAL) {
            RangeSelector {
                css {
                    alignSelf = AlignSelf.center
                }
                this.extremeProbabilityMode = extremeProbabilityMode
                this.zoomState = zoomState
                this.elementWidth = props.elementWidth
                this.onSelectRange = { preset: ZoomPreset ->
                    val paperCenter = zoomState.contentToPaper(preset.center)
                    val viewportCenter = props.elementWidth / 2
                    val pan = paperCenter - viewportCenter
                    val newState = PZState(zoomState.params, preset.zoom, pan)
                    ctl.state = newState
                }
            }
        }
    }
})

external interface BinaryPredInputProps : PredictionInputProps {
    var extremeProbabilityMode: ExtremeProbabilityMode?
}

val BinaryPredInput = FC<BinaryPredInputProps> { props->
    val propDist = (props.dist as? BinaryDistribution)
    var previewDist by useState(propDist)
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER
    useEffect(propDist?.yesProb) { previewDist = propDist }
    val extremeProbabilityMode = props.extremeProbabilityMode ?: props.question?.extremeProbabilityMode ?: ExtremeProbabilityMode.NORMAL

    val realSize = useElementSize<HTMLElement>()
    val clickRE = usePureClick<HTMLElement> { ev->
        if (previewDist == null) {
            val elem = ev.currentTarget as HTMLElement
            val rect = elem.getBoundingClientRect()
            val width = rect.width
            val x = ev.clientX - rect.left
            val zoomParams = PZParams(viewportWidth = width, contentDomain = 0.0..100.0, sidePad = SIDE_PAD)
            val zoomState = PZState(zoomParams)
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
            this.extremeProbabilityMode = extremeProbabilityMode
            this.onChange = { newDist, isCommit->
                previewDist = (newDist as BinaryDistribution)
                props.onChange?.invoke(newDist, isCommit)
            }
            this.disabled = props.disabled
        }
    }
}
