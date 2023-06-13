package components.redesign.questions.predictions

import BinaryHistogram
import browser.window
import components.redesign.basic.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import csstype.*
import dom.events.MouseEvent
import dom.html.HTMLCanvasElement
import dom.html.HTMLDivElement
import dom.html.HTMLElement
import dom.html.RenderingContextId
import emotion.react.css
import hooks.*
import kotlinx.js.jso
import react.*
import react.dom.html.ReactHTML.canvas
import react.dom.html.ReactHTML.div
import tools.confido.question.Question
import tools.confido.spaces.NumericSpace
import tools.confido.spaces.NumericValue
import tools.confido.utils.toFixed
import utils.addEventListener
import utils.panzoom1d.PZParams
import utils.panzoom1d.usePanZoom
import utils.removeEventListener
import web.events.Event

external interface BinaryPredictionHistogramProps : PropsWithElementSize {
    var question: Question
    var binaryHistogram: BinaryHistogram?
}

private data class BinRectangle(
    val id: Int,
    val count: Int,
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
    val layoutMode = useContext(LayoutModeContext)
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

    val histogram = props.binaryHistogram

    val yScale = GRAPH_HEIGHT / (histogram?.bins?.maxOfOrNull { it.count }?.toDouble() ?: 1.0)

    val rectangles = useMemo(zoomState.pan, dpr, logicalWidth, zoomState.zoom, yScale, histogram) {
        histogram?.bins?.let { bins ->
            var off = (-zoomState.pan)
            bins.mapIndexed { index, bin ->
                val binRectWidth = (logicalWidth - 2 * SIDE_PAD) * bin.width * zoomState.zoom
                val x = off * dpr
                off += binRectWidth
                val y = (GRAPH_TOP_PAD + GRAPH_HEIGHT) * dpr
                val width = binRectWidth * dpr
                val height = -bin.count * yScale * dpr
                BinRectangle(index, bin.count, x, y, width, height, bin.min, bin.max)
            }
        } ?: emptyList()
    }

    val horizontalMarks = useMemo(histogram) {
        val max = histogram?.bins?.maxOfOrNull { it.count } ?: 1
        val base = maxOf(max / (HORIZONTAL_MARK_COUNT - 1), 1)

        val marks = (0..max step base).take(HORIZONTAL_MARK_COUNT).toMutableList()
        marks[marks.size - 1] = max

        marks.toList()
    }

    var hoveredBin by useState<Int?>(null)
    useEffect {
        // This must not be a fun, as referencing it through ::onMouseMove would result
        // in a different function being created in addEventListener and removeEventListener,
        // and removal would fail.
        val onMouseMove = { e: Event ->
            e as MouseEvent
            val target = canvas.current as HTMLCanvasElement
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

        window.addEventListener("mousemove", onMouseMove);
        cleanup {
            window.removeEventListener("mousemove", onMouseMove);
        }
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
            rectangles.map { rect ->
                fillStyle = if (rect.id == hoveredBin) {
                    Color("#7CF77F")
                } else {
                    Color("#8BF08E")
                }
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
        val flags = listOfNotNull(histogram?.median?.let {
            PredictionFlag(
                color = Color("#2AE6C9"),
                flagpole = it in zoomState.visibleContentRange,
                content = FlagContentValue.create {
                    color = Color("#FFFFFF")
                    value = NumericValue(NumericSpace(0.0, 100.0, decimals=0, unit="%"), it * 100)
                    title = "median"
                })
        }, histogram?.mean?.let {
            PredictionFlag(
                color = Color("#00C3E9"),
                flagpole = it in zoomState.visibleContentRange,
                content = FlagContentValue.create {
                    color = Color("#FFFFFF")
                    value = NumericValue(NumericSpace(0.0, 100.0, decimals=0, unit="%"), it * 100)
                    title = "mean"
                })
        })
        val flagPositions = listOfNotNull(histogram?.median?.let {
            zoomState.contentToViewport(it)
        }, histogram?.mean?.let {
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
                this.collapseUntilHovered = true
            }
        }

        val twoRows = useBreakpoints(true to 480,  default = false)

        val rows = if (twoRows) {
            listOf(
                rectangles.filterIndexed { idx, _ -> idx % 2 == 0 },
                rectangles.filterIndexed { idx, _ -> idx % 2 == 1 }
            )
        } else {
            listOf(rectangles)

        }

        rows.mapIndexed { rowIndex, rowRectangles ->
            div {
                css {
                    height = if (layoutMode >= LayoutMode.TABLET) 36.px else 28.px
                    marginTop = 4.px
                    flexGrow = number(0.0)
                    flexShrink = number(0.0)
                    alignSelf = AlignSelf.flexStart
                    color = rgba(0, 0, 0, 0.3)
                    position = Position.relative
                    fontFamily = sansSerif
                    fontWeight = integer(200)
                    whiteSpace = WhiteSpace.nowrap
                    textAlign = TextAlign.center
                    if (rowIndex > 0) {
                        marginTop = (-10).px
                    }
                }
                rowRectangles
                    .filter { it.rangeMidpoint in zoomState.visibleContentRange }
                    .forEachIndexed { idx, rect ->
                        div {
                            style = jso {
                                left = zoomState.contentToViewport(rect.rangeMidpoint).px
                                top = 0.pct
                                if (rect.id == hoveredBin) {
                                    color = rgba(0, 0, 0, 0.8)
                                }
                                transition = Transition(ident("color"), 0.2.s, 0.s)
                            }
                            css {
                                val xtrans = when (idx) {
                                    0 -> max((-50).pct, (-SIDE_PAD).px)
                                    else -> (-50).pct
                                }
                                transform = translatex(xtrans)
                                position = Position.absolute
                            }
                            val start = (rect.min * 100.0).toFixed(0)
                            val end = (rect.max * 100.0).toFixed(0)

                            val shortName = if (rect.min == 0.0) "<$end%"
                            else if (rect.max == 1.0) ">$start%"
                            else "${(100.0*(rect.min+rect.max)/2).toFixed(0)}%"

                            var longName = if (rect.min > 0.0 && rect.max < 1.0) "$start–$end%" else null
                            div {
                                div {
                                    css {
                                        fontSize = if (layoutMode >= LayoutMode.TABLET) 16.px else 15.px
                                    }
                                    +shortName
                                }
                            }
                            if (layoutMode >= LayoutMode.TABLET)
                            longName?.let {
                                div {
                                    css {
                                        fontSize = 10.px
                                    }
                                    +"($longName)"
                                }
                            }
                        }
                    }
            }
        }
        div {
            css {
                height = LABELS_HEIGHT.px
                flexGrow = number(0.0)
                flexShrink = number(0.0)
                fontSize = 12.px
                color = rgba(0, 0, 0, 0.3)
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

        div {
            css {
                height = LABELS_HEIGHT.px
                flexGrow = number(0.0)
                flexShrink = number(0.0)
                fontSize = 14.px
                color = rgba(0, 0, 0, 0.6)
                lineHeight = 14.52.px
                position = Position.absolute
                fontFamily = sansSerif
            }
            // A floating number that shows the number of predictions in the hovered bin
            hoveredBin?.let { id ->
                val rect = rectangles[id]
                val y = rect.y + rect.height - 6
                div {
                    style = jso {
                        left = zoomState.contentToViewport(rect.rangeMidpoint).px
                        top = y.px
                        transition = Transition(ident("color"), 0.2.s, 0.s)
                    }
                    css {
                        val xtrans = when (id) {
                            0 -> max((-50).pct, (-SIDE_PAD).px)
                            else -> (-50).pct
                        }
                        transform = translate(xtrans, (-50).pct)
                        position = Position.absolute
                    }
                    +"${rect.count}"
                }
            }
        }
    }
})