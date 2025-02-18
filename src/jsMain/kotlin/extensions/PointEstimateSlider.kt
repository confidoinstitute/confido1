package extensions

import components.redesign.basic.PropsWithElementSize
import components.redesign.basic.Stack
import components.redesign.basic.elementSizeWrapper
import components.redesign.basic.sansSerif
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.predictions.LABELS_HEIGHT
import components.redesign.questions.predictions.SIDE_PAD
import components.redesign.questions.predictions.SliderThumb
import components.redesign.questions.predictions.SliderTrack
import components.redesign.questions.predictions.ThumbKind
import csstype.*
import dom.html.HTMLElement
import emotion.react.css
import hooks.usePureClick
import react.*
import react.dom.html.ReactHTML.div
import tools.confido.question.PredictionTerminology
import tools.confido.question.Question
import tools.confido.spaces.NumericSpace
import utils.markSpacing
import utils.panzoom1d.*
import kotlinx.js.jso

external interface PointEstimateSliderProps : PropsWithElementSize {
    var space: NumericSpace
    var value: Double?
    var disabled: Boolean?
    var onChange: ((Double, Boolean) -> Unit)?
    var question: Question?
}

val PointEstimateSlider = elementSizeWrapper(FC<PointEstimateSliderProps>("PointEstimateSlider") { props ->
    val space = props.space
    val layoutMode = useContext(LayoutModeContext)
    val predictionTerminology = props.question?.predictionTerminology ?: PredictionTerminology.ANSWER
    val zoomParams = PZParams(
        contentDomain = space.range,
        viewportWidth = props.elementWidth,
        sidePad = SIDE_PAD,
        maxZoom = 10.0
    )
    val (panZoomRE, zoomState) = usePanZoom<HTMLElement>(zoomParams)

    val marks = useMemo(zoomState.paperWidth, space.min, space.max, space.unit) {
        markSpacing(zoomState.paperWidth, space.min, space.max) { v ->
            space.formatValue(v, false, true)
        }
    }
    val filteredMarks = marks.filter { it in zoomState.visibleContentRange }
    val disabled = props.disabled ?: false
    val interactVerb = if (layoutMode >= LayoutMode.TABLET) "Click" else "Tap"

    val createEstimateRE = usePureClick<HTMLElement> { ev ->
        if (props.value == null && !disabled) {
            val rect = (ev.currentTarget as HTMLElement).getBoundingClientRect()
            val x = ev.clientX - rect.left
            val newValue = zoomState.viewportToContent(x)
            props.onChange?.invoke(newValue, true)
        }
    }

    Stack {
        ref = panZoomRE
        css {
            position = Position.relative
            overflow = Overflow.hidden
        }

        // Empty space for signpost
        div {
            css {
                height = 50.px
            }
        }

        // Labels
        div {
            css {
                height = LABELS_HEIGHT.px
                flexGrow = number(0.0)
                flexShrink = number(0.0)
                fontSize = 12.px
                color = Color("rgba(0,0,0,30%)")
                lineHeight = 14.52.px
                position = Position.relative
                fontFamily = sansSerif
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
                    +space.formatValue(value, false, true)
                }
            }
        }

        // Slider area with track and thumb
        div {
            ref = createEstimateRE
            css {
                height = 40.px
                position = Position.relative
            }

            val current = props.value
            if (current == null) {
                if (!disabled) {
                    div {
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
                }
            } else {
                SliderTrack {
                    this.zoomState = zoomState
                    this.marks = filteredMarks
                }

                SliderThumb {
                    this.containerElement = props.element
                    this.zoomState = zoomState
                    this.formatSignpost = { v -> space.formatValue(v) }
                    this.kind = ThumbKind.Center
                    this.pos = current
                    this.disabled = disabled
                    this.signpostHeight = 60.0
                    this.onDrag = props.onChange
                }
            }
        }
    }
})
