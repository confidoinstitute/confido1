package components.redesign.questions

import components.questions.ConfidenceColor
import components.redesign.basic.PropsWithElementSize
import components.redesign.basic.Stack
import components.redesign.basic.elementSizeWrapper
import components.redesign.questions.SpaceZoomManager.Companion.SIDE_PAD
import csstype.*
import dom.html.HTMLCanvasElement
import dom.html.HTMLDivElement
import dom.html.RenderingContextId
import emotion.react.css
import hooks.useDPR
import hooks.useElementSize
import kotlinx.js.jso
import mui.material.Slider
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import space.kscience.dataforge.values.Value
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.spaces.Binner
import tools.confido.spaces.NumericSpace
import tools.confido.utils.toFixed

external interface NumericPredGraphProps : PropsWithElementSize {
    var space: NumericSpace
    var dist: ContinuousProbabilityDistribution?
    var preferredCICenter: Double?
    var zoomable: Boolean?
    var onZoomChange: ((Double, Double)->Unit)?
}

class SpaceZoomManager(
        val space: NumericSpace,
        val viewportWidth: Double,
        val xZoomFactor: Double = 1.0,
        val xPan:Double = 0.0
) {
    companion object {
        val SIDE_PAD = 27.0
    }

    val xScaleUnzoomed = (viewportWidth - 2*SIDE_PAD) / space.size
    val xScale = xScaleUnzoomed * xZoomFactor
    val graphFullWidth= space.size * xScale // width of the whole graph, in css pixels, at current zoom
    val fullWidth = graphFullWidth+ 2*SIDE_PAD // width of the whole graph, in css pixels, at current zoom, including side padding
    val xPanMax = maxOf(fullWidth - viewportWidth, 0.0)
    val xPanEffective = minOf(xPan, xPanMax)
    val leftPadVisible = maxOf(SIDE_PAD - xPanEffective, 0.0)
    val leftmostGraphPointPx = maxOf(xPanEffective - SIDE_PAD, 0.0) // from left side of full graph area
    val rightmostGraphPointPx = minOf(xPanEffective + viewportWidth, graphFullWidth)
    val visibleSubspace = space.subspace(leftmostGraphPointPx/xScale, rightmostGraphPointPx/xScale)
    val visibleGraphWidth = rightmostGraphPointPx - leftmostGraphPointPx

    //val marks = markSpacing(visibleGraphWidth, visibleSubspace.min, visibleSubspace.max, { visibleSubspace.formatValue(it, false, true) })
    val marks = markSpacing(graphFullWidth, space.min, space.max, { space.formatValue(it, false, true) })
        .filter{ it in visibleSubspace.min..visibleSubspace.max }

    fun space2canvasCssPx(x: Double) = (x - visibleSubspace.min)*xScale + leftPadVisible
}


val NumericPredGraph = elementSizeWrapper(FC<NumericPredGraphProps> { props->
    val ABOVE_GRAPH_PAD = 8.0
    val GRAPH_TOP_PAD = 33.0
    val GRAPH_HEIGHT = 131.0
    val LABELS_HEIGHT = 24.0
    val dist = props.dist
    val canvas = useRef<HTMLCanvasElement>()
    val space = props.space
    val dpr = useDPR()
    val desiredLogicalHeight = GRAPH_TOP_PAD + GRAPH_HEIGHT
    val desiredLogicalWidth = props.elementWidth
    val physicalWidth = (desiredLogicalWidth * dpr).toInt()
    val physicalHeight = (desiredLogicalHeight * dpr).toInt()
    val logicalWidth = physicalWidth.toDouble() / dpr // make sure we have whole number of phyisical pixels
    val logicalHeight = physicalHeight.toDouble() / dpr


    val confidences = listOf(
            ConfidenceColor(0.8, "#7ff7ff"),

    )
    val outsideColor = "#b08bff"

    var xZoomFactor by useState(1.0) // 1.0 = maximally unzoomed, always >= 1
    var xPan by useState(0.0) // pan along the x-axis, in CSS logical pixels (0 = leftmost part of graph is visible)

    val zoomMgr = SpaceZoomManager(space, logicalWidth, xZoomFactor, xPan)

    useEffect(xPan > zoomMgr.xPanMax) { if (xPan > zoomMgr.xPanMax) xPan = zoomMgr.xPanMax}

    val bins = (zoomMgr.visibleGraphWidth*dpr).toInt()
    val binner = Binner(zoomMgr.visibleSubspace, bins)


    fun space2canvasPhysPx(x: Double) = zoomMgr.space2canvasCssPx(x)*dpr


    val discretizedProbs = useMemo(props.dist, bins, zoomMgr.visibleSubspace.min, zoomMgr.visibleSubspace.max) {
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

    useEffect(xZoomFactor, xPan) {
        props.onZoomChange?.invoke(xZoomFactor, xPan)
    }

    useLayoutEffect(yTicks, yScale, physicalWidth, physicalHeight) {
        val context = canvas.current?.getContext(RenderingContextId.canvas)
        context?.apply {
            clearRect(0.0, 0.0, physicalWidth, physicalHeight)
            val left = (zoomMgr.leftPadVisible*dpr).toInt()
            yTicks.mapIndexed {index, yTick ->
                fillStyle = yTick.second
                fillRect(left+index, (GRAPH_TOP_PAD+GRAPH_HEIGHT)*dpr, 1, -yTick.first*yScale*dpr)
                //beginPath()
                //moveTo(left + index, (GRAPH_TOP_PAD + GRAPH_HEIGHT)*dpr)
                //lineTo(left + index, (GRAPH_TOP_PAD + GRAPH_HEIGHT - yTick.first*yScale)*dpr)
                //strokeStyle = yTick.second
                //stroke()
            }
            zoomMgr.marks.forEach { x->
                beginPath()
                moveTo(space2canvasPhysPx(x), 0)
                lineTo(space2canvasPhysPx(x), (GRAPH_TOP_PAD + GRAPH_HEIGHT)*dpr)
                strokeStyle = "rgba(0,0,0,30%)"
                stroke()
            }
        }
    }

    Stack {
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
                fontFamily = FontFamily.sansSerif
            }
            zoomMgr.marks.forEachIndexed {idx, value->
                div {
                    style = jso {
                        left = zoomMgr.space2canvasCssPx(value).px
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
    Stack {
        css(ClassName("debug")) {
            backgroundColor = Color("#ffee58")
        }
        table {
            css {
                "th,td" { border = Border(1.px, LineStyle.solid, Color("#333")) }
                borderCollapse = BorderCollapse.collapse
            }
            fun dbg(n: String, v: Any) = tr { td {+n}; td{
                +(if (v is Double) v.toFixed(1) else v.toString())}}
            dbg("dpr", dpr)
            dbg("logicalWidth", logicalWidth)
            dbg("physicalWidth", physicalWidth)
            dbg("xZoomFactor", xZoomFactor)
            dbg("xScale", zoomMgr.xScale.toString())
            dbg("visibleSpace", "${zoomMgr.visibleSubspace.min.toFixed(1)}..${zoomMgr.visibleSubspace.max.toFixed(1)}")
            dbg("marks", zoomMgr.marks.map{it.toFixed(0)}.joinToString(","))
        }
        Slider {
            min = 1.0
            max = 10.0
            step = 0.1
            value = xZoomFactor
            onChange = { _, v, _ -> xZoomFactor = v as Double }
        }
        Slider {
            min = 0.0
            max = zoomMgr.xPanMax
            step = 0.1
            value = zoomMgr.xPanEffective
            onChange = { _, v, _ -> xPan = v as Double }
        }

    }
})
