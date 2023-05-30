package components.redesign.questions.predictions

import BinaryHistogram
import components.redesign.basic.*
import csstype.*
import dom.html.HTMLCanvasElement
import dom.html.HTMLDivElement
import dom.html.HTMLElement
import dom.html.RenderingContextId
import emotion.react.css
import hooks.combineRefs
import hooks.useDPR
import hooks.usePureClick
import hooks.useWebSocket
import kotlinx.js.jso
import react.*
import react.dom.html.ReactHTML.canvas
import react.dom.html.ReactHTML.div
import tools.confido.question.Question
import tools.confido.spaces.NumericSpace
import tools.confido.spaces.NumericValue
import tools.confido.utils.toFixed
import utils.panzoom1d.PZParams
import utils.panzoom1d.usePanZoom

external interface BinaryPredictionHistogramProps : PropsWithElementSize {
    var question: Question
}

private data class BinRectangle(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val min: Double,
    val max: Double,
) {
    val rangeMidpoint = (min + max) / 2

    fun contains(posX: Double, posY: Double): Boolean {
        return if (height < 0) {
            posX >= x && posX <= x + width && posY <= y && posY >= y + height
        } else {
            posX >= x && posX <= x + width && posY >= y && posY <= y + height
        }
    }
}

val BinaryPredictionHistogram: FC<BinaryPredictionHistogramProps> = elementSizeWrapper(FC { props ->
    // TODO: This is heavily based on NumericPredGraph, consider refactoring
    val ABOVE_GRAPH_PAD = 8.0
    val GRAPH_TOP_PAD = 33.0
    val GRAPH_HEIGHT = 131.0
    val SIDE_PAD = 27.0
    val LABELS_HEIGHT = 24.0
    val HORIZONTAL_MARK_COUNT = 6
    val canvas = useRef<HTMLCanvasElement>()
    val dpr = useDPR()
    val desiredLogicalHeight = GRAPH_TOP_PAD + GRAPH_HEIGHT
    val desiredLogicalWidth = props.elementWidth
    val physicalWidth = (desiredLogicalWidth * dpr).toInt()
    val physicalHeight = (desiredLogicalHeight * dpr).toInt()
    val logicalWidth = physicalWidth.toDouble() / dpr // make sure we have whole number of physical pixels
    val logicalHeight = physicalHeight.toDouble() / dpr

    val zoomParams = PZParams(contentDomain = 0.0..1.0, viewportWidth = props.elementWidth, sidePad = SIDE_PAD)
    val (panZoomRE, zoomState) = usePanZoom<HTMLDivElement>(zoomParams)
    val clickRE = usePureClick<HTMLDivElement> { ev ->
        val rect = (ev.currentTarget as HTMLDivElement).getBoundingClientRect()
        val x = ev.clientX - rect.left
        val xSp = zoomState.viewportToContent(x.toDouble())
        val y = ev.clientY - rect.top
        val yRel = y / rect.height
    }

    val histogram = useWebSocket<BinaryHistogram>("/state${props.question.urlPrefix}/histogram")

    // TODO: Send
    val bins = 9

    val yScale = GRAPH_HEIGHT / (histogram.data?.bins?.maxOfOrNull { it.count }?.toDouble() ?: 1.0)

    val rectangles = useMemo(zoomState.pan, dpr, logicalWidth, bins, zoomState.zoom, yScale, histogram) {
        histogram.data?.bins?.mapIndexed { index, bin ->
            val left = (-zoomState.pan).toInt()
            val binRectWidth = (logicalWidth - 2 * SIDE_PAD) / (bins + 1) * zoomState.zoom
            val x = (left + index * binRectWidth) * dpr
            val y = (GRAPH_TOP_PAD + GRAPH_HEIGHT) * dpr
            val width = binRectWidth * dpr
            val height = -bin.count * yScale * dpr
            BinRectangle(x, y, width, height, bin.min, bin.max)
        } ?: emptyList()
    }

    val horizontalMarks = useMemo(histogram) {
        val max = histogram.data?.bins?.maxOfOrNull { it.count } ?: 1
        val base = maxOf(max / (HORIZONTAL_MARK_COUNT - 1), 1)

        val marks = (0..max step base).take(HORIZONTAL_MARK_COUNT).toMutableList()
        marks[marks.size - 1] = max

        marks.toList()
    }

    var hoveredBin by useState<Int?>(null)
    canvas.current?.onmousemove = { e ->
        val target = e.target as HTMLCanvasElement
        val boundingRect = target.getBoundingClientRect()
        val x = e.clientX - boundingRect.left
        val y = e.clientY - boundingRect.top

        var foundIndex: Int? = null
        for ((index, rect) in rectangles.withIndex()) {
            if (rect.contains(x, y)) {
                foundIndex = index
                break
            }
        }
        hoveredBin = foundIndex
    }

    useLayoutEffect(
        yScale,
        physicalWidth,
        physicalHeight,
        dpr,
        hoveredBin,
        rectangles,
        horizontalMarks,
    ) {
        val context = canvas.current?.getContext(RenderingContextId.canvas)
        context?.apply {
            clearRect(0.0, 0.0, physicalWidth, physicalHeight)
            rectangles.mapIndexed { index, rect ->
                fillStyle = Color("#8BF08E")
                strokeStyle = rgba(0, 0, 0, 0.1)
                fillRect(rect.x, rect.y, rect.width, rect.height)
                beginPath()
                moveTo(rect.x, rect.y)
                lineTo(rect.x, rect.y + rect.height)
                lineTo(rect.x + rect.width, rect.y + rect.height)
                lineTo(rect.x + rect.width, rect.y)
                stroke()
            }
            horizontalMarks.forEach { markY ->
                beginPath()
                val y = logicalHeight * dpr - markY * yScale * dpr
                moveTo(SIDE_PAD, y)
                lineTo(physicalWidth - SIDE_PAD, y)
                strokeStyle = rgba(0, 0, 0, 0.1)
                stroke()
            }
        }
    }

    Stack {
        ref = combineRefs(panZoomRE, clickRE).unsafeCast<Ref<HTMLElement>>()
        css {
            position = Position.relative
            overflow = Overflow.hidden
        }
        canvas {
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
        val flags = listOfNotNull(histogram.data?.median?.let {
            PredictionFlag(
                color = Color("#2AE6C9"),
                flagpole = it in zoomState.visibleContentRange,
                content = FlagContentValue.create {
                    color = Color("#FFFFFF")
                    value = NumericValue(NumericSpace(0.0, 100.0, decimals=0, unit="%"), it * 100)
                    title = "median"
                })
        }, histogram.data?.mean?.let {
            PredictionFlag(
                color = Color("#00C3E9"),
                flagpole = it in zoomState.visibleContentRange,
                content = FlagContentValue.create {
                    color = Color("#FFFFFF")
                    value = NumericValue(NumericSpace(0.0, 100.0, decimals=0, unit="%"), it * 100)
                    title = "mean"
                })
        })
        val flagPositions = listOfNotNull(histogram.data?.median?.let {
            zoomState.contentToViewport(it)
        }, histogram.data?.mean?.let {
            zoomState.contentToViewport(it)
        })

        div {
            style = jso {
                this.width = logicalWidth.px
                this.height = logicalHeight.px
            }
            css {
                position = Position.absolute
                top = ABOVE_GRAPH_PAD.px
            }
            PredictionFlags {
                this.flags = flags
                this.flagPositions = flagPositions
            }
        }

        div {
            style = jso {
                this.width = logicalWidth.px
                this.height = logicalHeight.px
            }
            css {
                position = Position.absolute
                top = ABOVE_GRAPH_PAD.px
            }
            PredictionFlags {
                this.flags = flags
                this.flagPositions = flagPositions
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
            rectangles
                .filter { it.rangeMidpoint in zoomState.visibleContentRange }
                .forEachIndexed { idx, rect ->
                    div {
                        style = jso {
                            left = zoomState.contentToViewport(rect.rangeMidpoint).px
                            top = 50.pct
                        }
                        css {
                            val xtrans = when (idx) {
                                0 -> max((-50).pct, (-SIDE_PAD).px)
                                else -> (-50).pct
                            }
                            transform = translate(xtrans, (-50).pct)
                            position = Position.absolute
                        }
                        val start = (rect.min * 100.0).toFixed(0)
                        val end = (rect.max * 100.0).toFixed(0)
                        +"$start–$end%"
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
                position = Position.absolute
                fontFamily = sansSerif
            }
            horizontalMarks.forEach { value ->
                div {
                    style = jso {
                        left = 12.px
                        top = (logicalHeight - value * yScale).px
                    }
                    css {
                        transform = translate(0.pct, (-50).pct)
                        position = Position.absolute
                    }
                    +"$value"
                }
            }
        }
    }
})