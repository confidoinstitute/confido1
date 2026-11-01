package tools.confido.web.components.questions.predictions

import tools.confido.web.utils.localX
import tools.confido.web.components.questions.*
import tools.confido.web.components.basic.*
import tools.confido.web.components.basic.Stack
import web.cssom.*
import web.html.*
import emotion.react.*
import tools.confido.web.hooks.*
import js.objects.unsafeJso as jso
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import tools.confido.distributions.*
import tools.confido.spaces.*
import tools.confido.web.utils.panzoom1d.PZController
import tools.confido.web.utils.panzoom1d.PZDragMode
import tools.confido.web.utils.panzoom1d.ZoomHint
import tools.confido.web.utils.panzoom1d.PZParams
import tools.confido.web.utils.panzoom1d.PZState
import tools.confido.web.utils.panzoom1d.usePanZoom
import web.canvas.CanvasRenderingContext2D
import web.canvas.ID
import web.dom.Node
import web.events.EventType
import web.keyboard.KeyboardEvent
import web.pointer.POINTER_LEAVE
import web.pointer.POINTER_MOVE
import web.pointer.PointerEvent
import tools.confido.utils.formatPercent
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import tools.confido.utils.List2
import tools.confido.web.components.forms.ButtonUnstyled

data class ConfidenceColor(
    val p: Double,
    val color: String,
)

external interface NumericPredGraphProps : PropsWithElementSize, BasePredictionGraphProps {
    override var space: NumericSpace
    override var dist: ContinuousProbabilityDistribution?
    override var resolution: NumericValue?
    var graphHeight: Double?
    var preferredCICenter: Double?
    var zoomable: Boolean?
    var onZoomChange: ((PZState, List<Double>)->Unit)? // args: zoom state & visible marks
    var dimLines: Boolean?
    var maxZoom: Double?
    var onGraphClick: ((xSpace: Double, yRel: Double) -> Unit)?
    /** Scale factor for text (mark labels, flag values) and mark density —
     *  presenter screens pass >1 together with a taller [graphHeight]. */
    var fontScale: Double?
    /** Mouse hover shows a cursor line with the value under it and splits the
     *  density into P(below)/P(above) halves. Defaults to true. On input graphs
     *  the split needs Shift held, as the CI coloring is the editing feedback. */
    var hoverInsights: Boolean?
    /** Receives the graph's pan/zoom controller, so a companion widget (the input
     *  slider below) can forward its wheel events and zoom the graph too. */
    var zoomCtlRef: RefObject<PZController>?
}


// val marks = markSpacing(graphFullWidth, space.min, space.max, { space.formatValue(it, false, true) })
//     .filter{ it in visibleSubspace.min..visibleSubspace.max }

val SIDE_PAD = 27.0
val LABELS_HEIGHT = 24.0
val NumericPredGraph: FC<NumericPredGraphProps> = elementSizeWrapper(FC<NumericPredGraphProps>("NumericPredGraph") { props->
    val ABOVE_GRAPH_PAD = 8.0
    val GRAPH_TOP_PAD = 33.0
    val graphHeight = props.graphHeight ?: 131.0
    val dist = props.dist
    val canvas = useRef<HTMLCanvasElement>()
    val space = props.space
    val dpr = useDPR()
    val desiredLogicalHeight = GRAPH_TOP_PAD + graphHeight
    val desiredLogicalWidth = props.elementWidth
    val physicalWidth = (desiredLogicalWidth * dpr).toInt()
    val physicalHeight = (desiredLogicalHeight * dpr).toInt()
    val logicalWidth = physicalWidth.toDouble() / dpr // make sure we have whole number of physical pixels
    val logicalHeight = physicalHeight.toDouble() / dpr

    val zoomParams = PZParams(contentDomain = space.range, viewportWidth = props.elementWidth, sidePad = SIDE_PAD,
        maxZoom = props.maxZoom ?: 10.0)
    val zoom = useGraphZoom(zoomParams, space, listOfNotNull(dist), props.initialView, props.onViewChange)
    val zoomState = zoom.state
    props.zoomCtlRef?.current = zoom.ctl
    val clickRE = usePureClick<HTMLDivElement> { ev->
        // A click while armed backs out of zoom selection rather than predicting.
        if (zoom.armed) { zoom.disarm(); return@usePureClick }
        val el = ev.currentTarget as HTMLDivElement
        val rect = el.getBoundingClientRect()
        val x = el.localX(ev.clientX.toDouble())
        val xSp = zoomState.viewportToContent(x)
        val y = ev.clientY - rect.top
        val yRel = y / rect.height
        props.onGraphClick?.invoke(xSp, yRel)
    }
    val visibleSubspace = useMemo(space.min, space.max, zoomState.visibleContentRange.start, zoomState.visibleContentRange.endInclusive) {
        space.subspace(zoomState.visibleContentRange.start, zoomState.visibleContentRange.endInclusive)
    }
    val fontScale = props.fontScale ?: 1.0
    // markSpacing assumes ~8px glyphs; scaled-up labels claim proportionally
    // more room, which dividing the available width models exactly.
    val marks = useMemo(zoomState.paperWidth, space.min, space.max, space.unit, fontScale) {
        markSpacing(zoomState.paperWidth / fontScale, space.min, space.max, space::formatMark)
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
    // Canvas fillStyle wants plain strings.
    val belowColor = splitBelowColor.toString()
    val aboveColor = splitAboveColor.toString()

    // Hover position in viewport px (not content coords), so a wheel zoom under a
    // stationary mouse keeps reading out whatever is under the pointer.
    val hoverEnabled = props.hoverInsights ?: true
    val isInput = props.isInput ?: false
    var hoverPx by useState<Double>()
    val shiftHeld = useModifierKey(ModifierKey.SHIFT, enabled = hoverEnabled && isInput)
    // Native listeners, not React's onPointerMove: usePan's draggable stops
    // pointermove propagation, so React's root-delegated handler never sees it.
    val hoverMoveRE = useEventListener<HTMLDivElement, PointerEvent>(PointerEvent.POINTER_MOVE) { ev ->
        // Mouse only: touch gestures belong to pan-zoom, and a held button means
        // a pan or range selection is under way.
        hoverPx = if (ev.pointerType != "mouse" || ev.buttons.unsafeCast<Int>() != 0) null
            else (ev.currentTarget as HTMLElement).localX(ev.clientX.toDouble())
    }
    val hoverLeaveRE = useEventListener<HTMLDivElement, PointerEvent>(PointerEvent.POINTER_LEAVE) { hoverPx = null }
    val rootRef = combineRefs(zoom.ref, clickRE, hoverMoveRE, hoverLeaveRE)
    val contentViewport = zoomState.leftPadVisible..(zoomState.params.viewportWidth - zoomState.rightPadVisible)
    val cursorPx = hoverPx?.takeIf { hoverEnabled && it in contentViewport }
    val cursorX = cursorPx?.let { zoomState.viewportToContent(it) }
    val splitX = cursorX?.takeIf { dist != null && (!isInput || shiftHeld) }


    val bins = (zoomState.visibleContentWidth*dpr).toInt()
    val binner = Binner(visibleSubspace, bins)


    fun space2canvasPhysPx(x: Double) = zoomState.contentToViewport(x)*dpr


    val discretizedProbs = useMemo(props.dist, bins, zoomState.visibleContentRange.start, zoomState.visibleContentRange.endInclusive) {
        binner.binRanges.map { props.dist?.densityBetween(it) ?: 0.0 }
    }
    val ghost = props.ghostDist as? ContinuousProbabilityDistribution
    val readout = cursorX?.let { space.formatAtResolution(it, zoomState.params.contentWidth / zoomState.paperWidth) }
    val graphSplit = splitX?.let { x -> dist?.let { d -> readout?.let { label ->
        GraphSplit(x, label, d.cdf(x))
    } } }
    // Mirrored to a sibling description (e.g. PredictionDescription) if one listens.
    val splitChannel = use(GraphSplitContext)
    useEffect(graphSplit) { splitChannel?.publish(graphSplit) }
    useEffectWithCleanup(splitChannel) { onCleanup { splitChannel?.publish(null) } }
    val ghostProbs = useMemo(ghost, bins, zoomState.visibleContentRange.start, zoomState.visibleContentRange.endInclusive) {
        ghost?.let { g -> binner.binRanges.map { g.densityBetween(it) } }
    }
    //val maxDensity = (discretizedProbs.maxOrNull() ?: 1.0)
    // The ghost shares the density scale, so both curves stay comparable even when
    // the historical peak is the taller one.
    val maxDensity = maxOf(dist?.maxDensity ?: 1.0, ghost?.maxDensity ?: 0.0)

    val confidenceIntervals = if (dist == null) emptyList()
        else confidences.map {
        Pair(dist.confidenceInterval(it.p, props.preferredCICenter ?: dist.preferredCICenter), it.color)
    }
    fun barColor(x: Double) = if (splitX != null) {
        if (x < splitX) belowColor else aboveColor
    } else confidenceIntervals.find {
        x in it.first
    }?.second ?: outsideColor

    val yTicks = (0 until bins).map { bin -> discretizedProbs[bin] to barColor(binner.binMidpoints[bin]) }

    val yScale = graphHeight / maxDensity

    useLayoutEffect(yTicks, yScale, physicalWidth, physicalHeight, ghostProbs, cursorPx) {
        val context = canvas.current?.getContext(CanvasRenderingContext2D.ID)
        context?.apply {
            clearRect(0.0, 0.0, physicalWidth.toDouble(), physicalHeight.toDouble())
            val left = (zoomState.leftPadVisible*dpr).toInt()
            yTicks.mapIndexed {index, yTick ->
                fillStyle = yTick.second
                fillRect((left+index).toDouble(), (GRAPH_TOP_PAD+graphHeight)*dpr, 1.0, -yTick.first*yScale*dpr)
                //beginPath()
                //moveTo(left + index, (GRAPH_TOP_PAD + GRAPH_HEIGHT)*dpr)
                //lineTo(left + index, (GRAPH_TOP_PAD + GRAPH_HEIGHT - yTick.first*yScale)*dpr)
                //strokeStyle = yTick.second
                //stroke()
            }
            filteredMarks.forEach { x->
                beginPath()
                moveTo(space2canvasPhysPx(x), 0.0)
                lineTo(space2canvasPhysPx(x), (GRAPH_TOP_PAD + graphHeight)*dpr)
                strokeStyle = "rgba(0,0,0,${if (props.dimLines?:false) 15 else 30}%)"
                stroke()
            }
            // Scrub ghost: the historical density as an outline curve over the fill.
            ghostProbs?.let { probs ->
                beginPath()
                probs.forEachIndexed { index, p ->
                    val x = (left + index).toDouble() + 0.5
                    val y = (GRAPH_TOP_PAD + graphHeight - p * yScale) * dpr
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                strokeStyle = "rgba(60,60,60,0.8)"
                lineWidth = 1.5 * dpr
                stroke()
                lineWidth = 1.0
            }
            cursorPx?.let { px ->
                // Snapped to the middle of a physical pixel so a 1px stroke stays crisp.
                val x = floor(px * dpr) + 0.5
                beginPath()
                moveTo(x, 0.0)
                lineTo(x, (GRAPH_TOP_PAD + graphHeight) * dpr)
                strokeStyle = "rgba(0,0,0,0.65)"
                stroke()
            }
        }
    }
    Stack {
        ref = rootRef.unsafeCast<Ref<HTMLElement>>()
        css {
            position = Position.relative
            overflow = Overflow.hidden
            graphZoomCursor(zoom)
        }
        // Keyed: the DSL drops absent conditional children instead of leaving a
        // placeholder, so e.g. the ghost chip appearing would shift and remount
        // every later sibling (canvas, flags) by position.
        if (props.interactive?:true)
        GraphButtons {
            key = "buttons"
            +props
            leading = zoom.pill
            presenterView = zoomState.visibleDomainRange.takeIf { zoomState.zoom > 1.0 }
        }
        if (ghost != null && props.ghostLabel != null) {
            div {
                key = "ghostChip"
                css {
                    position = Position.absolute
                    top = 8.px
                    left = 10.px
                    zIndex = integer(5)
                    if (props.onGhostDismiss == null) pointerEvents = None.none
                    display = Display.flex
                    alignItems = AlignItems.center
                    gap = 6.px
                    backgroundColor = Color("rgba(255,255,255,0.92)")
                    border = Border(0.5.px, LineStyle.solid, Color("#CCCCCC"))
                    borderRadius = 5.px
                    padding = Padding(3.px, 7.px)
                    fontFamily = sansSerif
                    fontSize = 11.px
                    lineHeight = 14.px
                    fontWeight = integer(600)
                    color = Color("#555555")
                }
                +props.ghostLabel!!
                props.onGhostDismiss?.let { dismiss ->
                    div {
                        css {
                            cursor = Cursor.pointer
                            color = Color("#999999")
                            hover { color = Color("#555555") }
                        }
                        +"✕"
                        onClick = { dismiss() }
                    }
                }
            }
        }
        zoom.selectionOverlay(ABOVE_GRAPH_PAD, GRAPH_TOP_PAD + graphHeight)?.let { +it }
        ReactHTML.canvas {
            key = "canvas"
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
        if (props.isGroup) {
            val flags = listOfNotNull(props.dist?.let {
                PredictionFlag(
                    color = MainPalette.center.color,
                    flagpole = it.median in zoomState.visibleContentRange,
                    content = FlagContentValue.create {
                    color = MainPalette.center.text.color
                    value = NumericValue(it.space, it.median)
                    title = "group estimate"
                    this.fontScale = fontScale
                })
            }, props.resolution?.let {
                PredictionFlag(
                    color = Color("#00CC2E"),
                    flagpole = it.value in zoomState.visibleContentRange,
                    content = FlagContentValue.create {
                    color = Color("#FFFFFF")
                    value = it
                    title = "correct answer"
                    this.fontScale = fontScale
                })
            })
            val flagPositions = listOfNotNull(props.dist?.let {
                zoomState.contentToViewport(it.median)
            }, props.resolution?.let {
                zoomState.contentToViewport(it.value)
            })

            div {
                key = "flags"
                style = jso {
                    this.width = logicalWidth.px
                    this.height = logicalHeight.px
                }
                css {
                    position = Position.absolute
                    top = ABOVE_GRAPH_PAD.px
                    // Display-only flags on the group graph; without this the
                    // overlay eats clicks aimed at the ghost chip's dismiss button.
                    pointerEvents = None.none
                }
                PredictionFlags {
                    this.flags = flags
                    this.flagPositions = flagPositions
                }
            }
        }
        div {
            key = "labels"
            css {
                height = (LABELS_HEIGHT * fontScale).px
                flexGrow = number(0.0)
                flexShrink = number(0.0)
                fontSize = (12 * fontScale).px
                color = Color("rgba(0,0,0,30%)")
                lineHeight = (14.52 * fontScale).px
                position = Position.relative
                fontFamily = sansSerif
            }
            // Lives in the label row rather than the plot, where the value flags
            // (which can sit anywhere from mid-height down) would overlap it.
            val cursorReadout = if (cursorPx != null && readout != null) CursorReadout(
                cursorPx, logicalWidth, readout,
                below = graphSplit?.let { ReadoutChip("← ${formatPercent(it.below)}", splitBelowColor) },
                above = graphSplit?.let { ReadoutChip("${formatPercent(1 - it.below)} →", splitAboveColor) },
                fontScale = fontScale,
            ) else null
            filteredMarks.forEachIndexed {idx, value->
                val markPx = zoomState.contentToViewport(value)
                val markLabel = space.formatMark(value, marks.step)
                val markHalf = markLabel.length * 3.5 * fontScale
                val covered = cursorReadout?.covered?.let { markPx + markHalf > it.start && markPx - markHalf < it.endInclusive } ?: false
                div {
                    key = value.toString()
                    style = jso {
                        left = markPx.px
                        top = 50.pct
                        if (covered) visibility = Visibility.hidden
                    }
                    css {
                        val xtrans = when(idx) {
                            0 -> max((-50).pct, (-SIDE_PAD).px)
                            else -> (-50).pct
                        }
                        transform = translate(xtrans, (-50).pct)
                        position = Position.absolute
                    }
                    + markLabel
                }
            }
            cursorReadout?.render(this)
        }
    }

})

external interface FlagContentValueProps : Props {
    var color: Color
    var value: Value
    var title: String
    var fontScale: Double?
}

val FlagContentValue = memo(FC<FlagContentValueProps> {props ->
    val s = props.fontScale ?: 1.0
    Stack {
        css {
            alignItems = AlignItems.center
            color = props.color
            fontFamily = sansSerif
        }
        div {
            css {
                fontSize = (20 * s).px
                lineHeight = (24 * s).px
                fontWeight = integer(700)
            }
            +props.value.format()
        }
        div {
            css {
                fontSize = (8 * s).px
                lineHeight = (8 * s).px
                fontWeight = integer(600)
            }
            +props.title
        }
    }
})
