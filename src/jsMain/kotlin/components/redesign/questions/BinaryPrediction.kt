package components.redesign.forms

import components.redesign.basic.*
import components.redesign.questions.*
import csstype.*
import dom.html.HTMLElement
import emotion.react.*
import hooks.*
import kotlinx.js.*
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.distributions.BinaryDistribution
import tools.confido.spaces.NumericSpace
import kotlin.math.*

external interface BinaryPredictionProps : Props {
    var yesProb: Double?
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
            fontFamily = FontFamily.sansSerif
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
                            fontWeight = FontWeight.bold
                            fontSize = 50.px
                            lineHeight = 60.px
                        }
                        +"${round(prob * 100)}%"
                    }
                    div {
                        css {
                            fontWeight = FontWeight.bold
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

val BinaryPrediction = FC<BinaryPredictionProps> {props ->
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
        }
        proportionalCircle("No", Color("#FF5555"), props.yesProb?.let {1 - it}, size = circleSize)
        proportionalCircle("Yes", Color("#00CC2E"), props.yesProb, size = circleSize)
    }
}

external interface BinaryPredSliderProps : PredictionInputProps, PropsWithElementSize {
}
val BIN_PRED_SPACE = NumericSpace(0.0, 100.0, unit="%")
val BinaryPredSlider = elementSizeWrapper(FC<BinaryPredSliderProps> { props->
    val zoomMgr = SpaceZoomManager(BIN_PRED_SPACE, props.elementWidth)
    val propProb = (props.dist as? BinaryDistribution)?.yesProb
    var yesProb by useState<Double?>(propProb)
    useEffect(propProb) {
        propProb?.let { yesProb = propProb }
    }
    fun update(newProb: Double, isCommit: Boolean) {
        yesProb = newProb
        props.onChange?.invoke(BinaryDistribution(newProb))
        if (isCommit)
            props.onCommit?.invoke(BinaryDistribution(newProb))
    }

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
            fontFamily = FontFamily.sansSerif
            fontWeight = integer(600)
        }
        (0..100 step 10).forEachIndexed {idx, value->
            div {
                style = jso {
                    left = zoomMgr.space2canvasCssPx(value.toDouble()).px
                    top = 50.pct
                }
                css {
                    val xtrans = when(idx) {
                        0 -> max((-50).pct, (-SpaceZoomManager.SIDE_PAD).px)
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
        if (yesProb == null)
            div {
                key = "createEstimate"
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
                onClick = { ev ->
                    val newProb = zoomMgr.canvasCssPx2space(ev.nativeEvent.offsetX) / 100.0
                    update(newProb, true)
                }
            }
        else {
            SliderTrack {
                key = "track"
                this.zoomManager = zoomMgr
            }

            SliderThumb{
                key = "thumb_center"
                this.containerElement = props.element
                this.zoomManager = zoomMgr
                kind = ThumbKind.Center
                pos = 100.0 * yesProb!!
                onDrag = { pos, isCommit ->
                    update(pos/100.0, isCommit)
                }
                signpostEnabled = false
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
            fontFamily = FontFamily.sansSerif
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
                    left = zoomMgr.space2canvasCssPx(value.toDouble()).px
                    top = 50.pct
                }
                css {
                    val xtrans = when(idx) {
                        0 -> max((-50).pct, (-SpaceZoomManager.SIDE_PAD).px)
                        else -> (-50).pct
                    }
                    transform = translate(xtrans, (-50).pct)
                    position = Position.absolute
                }
                + text
            }
        }
    }
})

val BinaryPredInput = FC<PredictionInputProps> {props->
    val propProb = (props.dist as? BinaryDistribution)?.yesProb
    var previewProb by useState(propProb)
    useEffect(propProb) { previewProb = propProb }
    Stack {
        css {
            overflowX = Overflow.hidden
        }
        BinaryPrediction {
            this.yesProb = previewProb
        }
        BinaryPredSlider {
            this.space = props.space
            this.dist = props.dist
            this.onChange = {
                previewProb = (it as BinaryDistribution).yesProb
                props.onChange?.invoke(it)
            }
            this.onCommit = props.onCommit
        }
    }
}