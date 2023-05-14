package components.redesign.questions.predictions

import components.redesign.basic.*
import components.redesign.questions.*
import csstype.*
import dom.html.*
import emotion.react.*
import hooks.*
import kotlinx.js.*
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.distributions.*
import tools.confido.question.PredictionTerminology
import tools.confido.spaces.*
import utils.panzoom1d.PZParams
import utils.panzoom1d.PZState
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
                        css {
                            fontWeight = integer(700)
                            fontSize = 50.px
                            lineHeight = 60.px
                        }
                        +"${round(prob * 100)}%"
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
        val noColor = if (props.resolution?.value == true) { Color("#BBBBBB") } else { Color("#FF5555") }
        val yesColor = if (props.resolution?.value == false) { Color("#BBBBBB") } else { Color("#00CC2E") }
        proportionalCircle("No", noColor, props.dist?.yesProb?.let {1 - it}, size = circleSize)
        proportionalCircle("Yes", yesColor, props.dist?.yesProb, size = circleSize)
        if (props.interactive?:true)
        GraphButtons {
            +props
        }
    }
}

external interface BinaryPredSliderProps : PredictionInputProps, PropsWithElementSize {
}
val BIN_PRED_SPACE = NumericSpace(0.0, 100.0, unit="%")
val BinaryPredSlider = elementSizeWrapper(FC<BinaryPredSliderProps> { props->
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER
    val zoomParams = PZParams(viewportWidth = props.elementWidth, contentDomain = 0.0..100.0, sidePad = SIDE_PAD)
    val zoomState = PZState(zoomParams)
    val propProb = (props.dist as? BinaryDistribution)?.yesProb
    var yesProb by useState(propProb)
    val disabled = props.disabled?:false
    useEffect(propProb) {
        propProb?.let { yesProb = propProb }
    }
    fun update(newProb: Double, isCommit: Boolean) {
        yesProb = newProb
        props.onChange?.invoke(BinaryDistribution(newProb))
        if (isCommit)
            props.onCommit?.invoke(BinaryDistribution(newProb))
    }
    val clickRE = usePureClick<HTMLDivElement> { ev->

        if (yesProb == null) {
            val rect = (ev.currentTarget as HTMLElement).getBoundingClientRect()
            val x = ev.clientX - rect.left
            val newProb = zoomState.viewportToContent(x) / 100.0
            update(newProb, true)
        }
    }

    div {

        ref = clickRE
    div {
        key="percent_labels"
        css {
            height = 16.px
            flexGrow = number(0.0)
            flexShrink = number(0.0)
            fontSize = 10.px // TODO: use larger font on desktop
            color = Color("rgba(0,0,0,30%)")
            lineHeight = 12.1.px
            position = Position.relative
            fontFamily = sansSerif
            fontWeight = integer(600)
        }
        (0..100 step 10).forEachIndexed {idx, value->
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
                + "${value}%"
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
                    +"Tap here to create an ${predictionTerminology.aTerm}"// TODO choose "tap"/"click" based on device
                }
        } else {
            SliderTrack {
                key = "track"
                marks = (0..100 step 10).map{ it.toDouble() }
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
            }
        }

    }
    div {
        key="word_labels"
        css {
            height = 16.px
            flexGrow = number(0.0)
            flexShrink = number(0.0)
            fontSize = 10.px // TODO: use larger font on desktop
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
        ).forEachIndexed {idx, (value, text)->
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
                + text
            }
        }
    }
    }
})

val BinaryPredInput = FC<PredictionInputProps> { props->
    val propDist = (props.dist as? BinaryDistribution)
    var previewDist by useState(propDist)
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER
    useEffect(propDist?.yesProb) { previewDist = propDist }
    Stack {
        css {
            overflowX = Overflow.hidden
        }
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
            this.onChange = {
                previewDist = (it as BinaryDistribution)
                props.onChange?.invoke(it)
            }
            this.onCommit = props.onCommit
            this.disabled = props.disabled
        }
    }
}