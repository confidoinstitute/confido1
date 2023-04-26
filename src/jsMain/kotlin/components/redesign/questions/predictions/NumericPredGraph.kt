package components.redesign.questions.predictions

import components.questions.*
import components.redesign.basic.*
import components.redesign.basic.Stack
import csstype.*
import dom.html.*
import emotion.react.*
import hooks.*
import kotlinx.js.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import tools.confido.distributions.*
import tools.confido.spaces.*
import utils.panzoom1d.PZParams
import utils.panzoom1d.PZState
import utils.panzoom1d.usePanZoom

external interface NumericPredGraphProps : PropsWithElementSize, BasePredictionGraphProps {
    override var space: NumericSpace
    override var dist: ContinuousProbabilityDistribution?
    var preferredCICenter: Double?
    var zoomable: Boolean?
    var onZoomChange: ((PZState, List<Double>)->Unit)? // args: zoom state & visible marks
}


// val marks = markSpacing(graphFullWidth, space.min, space.max, { space.formatValue(it, false, true) })
//     .filter{ it in visibleSubspace.min..visibleSubspace.max }

val SIDE_PAD = 27.0
val LABELS_HEIGHT = 24.0
val NumericPredGraph = elementSizeWrapper(FC<NumericPredGraphProps>("NumericPredGraph") { props->
    val ABOVE_GRAPH_PAD = 8.0
    val GRAPH_TOP_PAD = 33.0
    val GRAPH_HEIGHT = 131.0
    val dist = props.dist
    val canvas = useRef<HTMLCanvasElement>()
    val space = props.space
    val dpr = useDPR()
    val desiredLogicalHeight = GRAPH_TOP_PAD + GRAPH_HEIGHT
    val desiredLogicalWidth = props.elementWidth
    val physicalWidth = (desiredLogicalWidth * dpr).toInt()
    val physicalHeight = (desiredLogicalHeight * dpr).toInt()
    val logicalWidth = physicalWidth.toDouble() / dpr // make sure we have whole number of physical pixels
    val logicalHeight = physicalHeight.toDouble() / dpr

    val zoomParams = PZParams(contentDomain = space.range, viewportWidth = props.elementWidth, sidePad = SIDE_PAD)
    val (panZoomRE, zoomState) = usePanZoom<HTMLDivElement>(zoomParams)
    val visibleSubspace = useMemo(space.min, space.max, zoomState.visibleContentRange.start, zoomState.visibleContentRange.endInclusive) {
        space.subspace(zoomState.visibleContentRange.start, zoomState.visibleContentRange.endInclusive)
    }
    val marks = useMemo(zoomState.paperWidth, space.min, space.max, space.unit) {
        markSpacing(zoomState.paperWidth, space.min, space.max, { space.formatValue(it, false, true) })
    }
    val filteredMarks = useMemo(marks, zoomState.pan) {
        marks.filter{ it in zoomState.visibleContentRange }
    }
    useEffect(zoomState.key()) {
        props.onZoomChange?.invoke(zoomState, filteredMarks)
    }

    val confidences = listOf(
            ConfidenceColor(0.8, "#7ff7ff"),

    )
    val outsideColor = "#b08bff"


    val bins = (zoomState.visibleContentWidth*dpr).toInt()
    val binner = Binner(visibleSubspace, bins)


    fun space2canvasPhysPx(x: Double) = zoomState.contentToViewport(x)*dpr


    val discretizedProbs = useMemo(props.dist, bins, zoomState.visibleContentRange.start, zoomState.visibleContentRange.endInclusive) {
        binner.binRanges.map { props.dist?.densityBetween(it) ?: 0.0 }
    }
    //val maxDensity = (discretizedProbs.maxOrNull() ?: 1.0)
    val maxDensity = dist?.maxDensity ?: 1.0

    val confidenceIntervals = if (dist == null) emptyList()
        else confidences.map {
        Pair(dist.confidenceInterval(it.p, props.preferredCICenter ?: dist.preferredCICenter), it.color)
    }
    fun barColor(x: Double) = confidenceIntervals.find {
        x in it.first
    }?.second ?: outsideColor

    val yTicks = (0 until bins).map { bin -> discretizedProbs[bin] to barColor(binner.binMidpoints[bin]) }

    val yScale = GRAPH_HEIGHT / maxDensity

    useLayoutEffect(yTicks, yScale, physicalWidth, physicalHeight) {
        val context = canvas.current?.getContext(RenderingContextId.canvas)
        context?.apply {
            clearRect(0.0, 0.0, physicalWidth, physicalHeight)
            val left = (zoomState.leftPadVisible*dpr).toInt()
            yTicks.mapIndexed {index, yTick ->
                fillStyle = yTick.second
                fillRect(left+index, (GRAPH_TOP_PAD+GRAPH_HEIGHT)*dpr, 1, -yTick.first*yScale*dpr)
                //beginPath()
                //moveTo(left + index, (GRAPH_TOP_PAD + GRAPH_HEIGHT)*dpr)
                //lineTo(left + index, (GRAPH_TOP_PAD + GRAPH_HEIGHT - yTick.first*yScale)*dpr)
                //strokeStyle = yTick.second
                //stroke()
            }
            filteredMarks.forEach { x->
                beginPath()
                moveTo(space2canvasPhysPx(x), 0)
                lineTo(space2canvasPhysPx(x), (GRAPH_TOP_PAD + GRAPH_HEIGHT)*dpr)
                strokeStyle = "rgba(0,0,0,30%)"
                stroke()
            }
        }
    }
    Stack {
        ref = panZoomRE.unsafeCast<Ref<HTMLElement>>()
        css {
            position = Position.relative
        }
        GraphButtons {
            isGroup = props.isGroup
            isInput = props.isInput
            question = props.question
        }
        ReactHTML.canvas {
            style = jso {
                this.width = logicalWidth.px
                this.height = logicalHeight.px
            }
            this.width = physicalWidth.toDouble()
            this.height = physicalHeight.toDouble()
            ref = canvas
            css {
                marginTop = ABOVE_GRAPH_PAD.px
                touchAction = None.none // fallback for browsers that do not support pan-y
                "&" {
                    touchAction = TouchAction.panY
                }
            }
        }
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
            filteredMarks.forEachIndexed {idx, value->
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
                    + space.formatValue(value, false, true)
                }
            }
        }
    }

})
