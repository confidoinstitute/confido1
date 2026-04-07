package extensions.update_scatter_plot

import components.redesign.basic.PropsWithElementSize
import components.redesign.basic.elementSizeWrapper
import emotion.react.css
import react.FC
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.g
import react.dom.svg.ReactSVG.line
import react.dom.svg.ReactSVG.svg
import react.dom.svg.ReactSVG.text
import react.dom.svg.StrokeLinecap
import react.dom.svg.TextAnchor
import react.useCallback
import react.useMemo
import react.useState
import tools.confido.distributions.BinaryDistribution
import tools.confido.utils.formatPercent
import web.cssom.*
import web.html.HTMLElement
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

external interface UpdateSlopeProps : PropsWithElementSize, UpdatePlotProps {
    var width: Double?
    var height: Double?
    var flipped: Boolean?
}

// A single hit from proximity detection
data class HoverHit(
    val participantIndex: Int,
    val segmentIndex: Int,
    val p1: Double,
    val p2: Double,
)

// Hover state with cursor position and all nearby hits
data class HoverState(
    val hits: List<HoverHit>,
    val svgX: Double,
    val svgY: Double,
)

// Compute minimum distance from a point to a line segment
private fun pointToSegmentDistance(
    px: Double, py: Double,
    x1: Double, y1: Double,
    x2: Double, y2: Double
): Double {
    val dx = x2 - x1
    val dy = y2 - y1
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0.0) return sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
    val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
    val tClamped = t.coerceIn(0.0, 1.0)
    val nearX = x1 + tClamped * dx
    val nearY = y1 + tClamped * dy
    return sqrt((px - nearX) * (px - nearX) + (py - nearY) * (py - nearY))
}

// Smooth nonlinear color gradient: gray at zero, green for positive, red for negative
private fun updateColor(delta: Double): String {
    val magnitude = abs(delta)
    val t = magnitude.pow(0.4).coerceIn(0.0, 1.0)
    val saturation = (t * 70).toInt()
    val hue = if (delta >= 0) 130 else 0
    val lightness = (65 - t * 20).toInt()
    return "hsl($hue, $saturation%, $lightness%)"
}

val UpdateSlopeChart = elementSizeWrapper(FC<UpdateSlopeProps> { props ->
    val (hoverInfo, setHoverInfo) = useState<HoverState?>(null)

    val numColumns = props.preds.firstOrNull()?.size ?: 2
    val flipped = props.flipped == true

    // Use elementWidth for responsive width, but require explicit height to avoid
    // feedback loops with elementSizeWrapper (the wrapper div grows with content height,
    // causing infinite resize cycles).
    val width = props.width ?: props.elementWidth.takeIf { it > 0 } ?: (numColumns * 200.0)
    val height = props.height ?: 600.0

    // Layout constants
    val padding = object {
        val top = if (flipped) 60.0 else 60.0
        val bottom = if (flipped) 60.0 else 60.0
        val left = if (flipped) 100.0 else 80.0
        val right = if (flipped) 60.0 else 80.0
    }

    val graphHeight = height - padding.top - padding.bottom - 2
    val graphWidth = width - padding.left - padding.right - 2

    // Coordinate mapping: probability value -> pixel coordinate
    fun probScale(value: Double): Double {
        return if (flipped) {
            // Horizontal: left=0%, right=100%
            padding.left + value * graphWidth
        } else {
            // Vertical: bottom=0%, top=100% (inverted Y)
            padding.top + graphHeight - (value * graphHeight)
        }
    }

    // Coordinate mapping: timepoint index -> pixel coordinate
    fun timeScale(colIndex: Int): Double {
        if (numColumns <= 1) return if (flipped) (height / 2) else (width / 2)
        return if (flipped) {
            // Vertical: top=first, bottom=last
            val step = graphHeight / (numColumns - 1)
            padding.top + (step * colIndex)
        } else {
            // Horizontal: left=first, right=last
            val step = graphWidth / (numColumns - 1)
            padding.left + (step * colIndex)
        }
    }

    // Map probability + timepoint to SVG x,y
    fun toX(prob: Double, colIndex: Int): Double = if (flipped) probScale(prob) else timeScale(colIndex)
    fun toY(prob: Double, colIndex: Int): Double = if (flipped) timeScale(colIndex) else probScale(prob)

    // Jitter perpendicular to the probability axis
    fun getJitter(participantId: Int, colIndex: Int): Pair<Double, Double> {
        val base = (participantId * 31 + colIndex * 17) % 100
        val offset = (base - 50) / 50.0 * 10.0
        return if (flipped) Pair(0.0, offset) else Pair(offset, 0.0)
    }

    // Pre-compute line segment positions for hover detection
    data class SegmentPos(
        val pIndex: Int,
        val cIndex: Int,
        val x1: Double, val y1: Double,
        val x2: Double, val y2: Double,
        val p1: Double, val p2: Double,
    )

    val segments = useMemo(props.preds, width, height, flipped) {
        buildList {
            props.preds.forEachIndexed { pIndex, row ->
                for (cIndex in 0 until row.size - 1) {
                    val p1 = (row[cIndex]?.dist as? BinaryDistribution)?.yesProb ?: continue
                    val p2 = (row[cIndex + 1]?.dist as? BinaryDistribution)?.yesProb ?: continue
                    val (jx1, jy1) = getJitter(pIndex, cIndex)
                    val (jx2, jy2) = getJitter(pIndex, cIndex + 1)
                    add(SegmentPos(
                        pIndex, cIndex,
                        toX(p1, cIndex) + jx1, toY(p1, cIndex) + jy1,
                        toX(p2, cIndex + 1) + jx2, toY(p2, cIndex + 1) + jy2,
                        p1, p2,
                    ))
                }
            }
        }
    }

    // Set of active (highlighted) participant indices
    val activeParticipants = useMemo(hoverInfo) {
        hoverInfo?.hits?.map { it.participantIndex }?.toSet() ?: emptySet()
    }

    val hitThreshold = 15.0 // pixels

    val handleMouseMove = useCallback(segments, hitThreshold, width, height) { event: react.dom.events.MouseEvent<HTMLElement, *> ->
        val el = event.currentTarget
        val rect = el.getBoundingClientRect()
        // Convert client coords to SVG viewBox coords
        val mx = (event.clientX - rect.left) / rect.width * width
        val my = (event.clientY - rect.top) / rect.height * height

        val hits = mutableListOf<HoverHit>()
        val seenParticipants = mutableSetOf<Int>()

        for (seg in segments) {
            if (seg.pIndex in seenParticipants) continue
            val dist = pointToSegmentDistance(mx, my, seg.x1, seg.y1, seg.x2, seg.y2)
            if (dist <= hitThreshold) {
                hits.add(HoverHit(seg.pIndex, seg.cIndex, seg.p1, seg.p2))
                seenParticipants.add(seg.pIndex)
            }
        }

        // Also check dot proximity (endpoints)
        props.preds.forEachIndexed { pIndex, row ->
            if (pIndex in seenParticipants) return@forEachIndexed
            row.forEachIndexed { cIndex, pred ->
                val value = (pred?.dist as? BinaryDistribution)?.yesProb ?: return@forEachIndexed
                val (jx, jy) = getJitter(pIndex, cIndex)
                val dx = mx - (toX(value, cIndex) + jx)
                val dy = my - (toY(value, cIndex) + jy)
                if (sqrt(dx * dx + dy * dy) <= hitThreshold && pIndex !in seenParticipants) {
                    // Find the relevant segment for this participant
                    val segP1 = if (cIndex > 0) (row[cIndex - 1]?.dist as? BinaryDistribution)?.yesProb else null
                    val segP2 = if (cIndex < row.size - 1) (row[cIndex + 1]?.dist as? BinaryDistribution)?.yesProb else null
                    val p1 = segP1 ?: value
                    val p2 = segP2 ?: value
                    hits.add(HoverHit(pIndex, maxOf(0, cIndex - 1), p1, if (segP1 != null) value else p2))
                    seenParticipants.add(pIndex)
                }
            }
        }

        if (hits.isNotEmpty()) {
            setHoverInfo(HoverState(hits, mx, my))
        } else {
            setHoverInfo(null)
        }
    }

    val handleMouseLeave = useCallback { _: react.dom.events.MouseEvent<HTMLElement, *> ->
        setHoverInfo(null)
    }

    div {
        css {
            display = Display.flex
            justifyContent = JustifyContent.center
            overflow = Overflow.visible
            backgroundColor = NamedColor.white
            borderRadius = 12.px
            border = Border(1.px, LineStyle.solid, Color("#e2e8f0"))
            position = Position.relative
            this.width = 100.pct
            this.height = 100.pct
        }
        onMouseMove = handleMouseMove
        onMouseLeave = handleMouseLeave

        svg {
            viewBox = "0 0 $width $height"
            this.width = width.toDouble()
            this.height = height.toDouble()
            style = js("({ fontFamily: 'Inter, sans-serif', display: 'block', width: '100%', height: '100%' })")

            // --- Grid Lines (25%, 50%, 75%) ---
            listOf(0.25, 0.50, 0.75).forEach { value ->
                g {
                    key = value.toString()

                    if (flipped) {
                        // Vertical grid lines
                        val xPos = probScale(value)
                        line {
                            x1 = xPos; y1 = padding.top - 20
                            x2 = xPos; y2 = height - padding.bottom + 20
                            stroke = "#e2e8f0"; strokeWidth = 1.0; strokeDasharray = "4 4"
                        }
                        text {
                            x = xPos; y = padding.top - 25
                            textAnchor = TextAnchor.middle
                            fill = "#94a3b8"; fontSize = 12.0; fontWeight = "500"
                            +"${(value * 100).toInt()}%"
                        }
                    } else {
                        // Horizontal grid lines
                        val yPos = probScale(value)
                        line {
                            x1 = padding.left - 20; y1 = yPos
                            x2 = width - padding.right + 20; y2 = yPos
                            stroke = "#e2e8f0"; strokeWidth = 1.0; strokeDasharray = "4 4"
                        }
                        text {
                            x = padding.left - 30; y = yPos; dy = 4.0
                            textAnchor = TextAnchor.end
                            fill = "#94a3b8"; fontSize = 12.0; fontWeight = "500"
                            +"${(value * 100).toInt()}%"
                        }
                    }
                }
            }

            // --- Frame Borders (probability axis endpoints) ---
            if (flipped) {
                // Vertical lines at left (0%) and right (100%)
                line {
                    x1 = padding.left; y1 = padding.top - 20
                    x2 = padding.left; y2 = height - padding.bottom + 20
                    stroke = "#cbd5e1"; strokeWidth = 1.0
                }
                line {
                    x1 = width - padding.right; y1 = padding.top - 20
                    x2 = width - padding.right; y2 = height - padding.bottom + 20
                    stroke = "#cbd5e1"; strokeWidth = 1.0
                }
            } else {
                // Horizontal lines at top (100%) and bottom (0%)
                line {
                    x1 = padding.left - 20; y1 = padding.top
                    x2 = width - padding.right + 20; y2 = padding.top
                    stroke = "#cbd5e1"; strokeWidth = 1.0
                }
                line {
                    x1 = padding.left - 20; y1 = height - padding.bottom
                    x2 = width - padding.right + 20; y2 = height - padding.bottom
                    stroke = "#cbd5e1"; strokeWidth = 1.0
                }
            }

            // --- Axis Labels (Yes/No) ---
            if (flipped) {
                // Yes on right, No on left
                text {
                    x = probScale(1.0); y = height - padding.bottom + 40
                    textAnchor = TextAnchor.middle
                    fill = "#00cc2e"; fontWeight = "600"; fontSize = 14.0
                    +"Yes"
                }
                text {
                    x = probScale(0.0); y = height - padding.bottom + 40
                    textAnchor = TextAnchor.middle
                    fill = "#ff5555"; fontWeight = "600"; fontSize = 14.0
                    +"No"
                }
            } else {
                // Yes on top, No on bottom
                text {
                    x = padding.left - 30; y = probScale(1.0); dy = 4.0
                    textAnchor = TextAnchor.end
                    fill = "#00cc2e"; fontWeight = "600"; fontSize = 14.0
                    +"Yes"
                }
                text {
                    x = padding.left - 30; y = probScale(0.0); dy = 4.0
                    textAnchor = TextAnchor.end
                    fill = "#ff5555"; fontWeight = "600"; fontSize = 14.0
                    +"No"
                }
            }

            // --- Column Labels ("Before", "After", etc.) ---
            for (i in 0 until numColumns) {
                val label = props.timepointLabels?.getOrNull(i)
                    ?: if (numColumns == 2) listOf("Before", "After")[i] else "Time ${i + 1}"
                if (flipped) {
                    // Labels on the left side
                    text {
                        x = padding.left - 10; y = timeScale(i); dy = 4.0
                        textAnchor = TextAnchor.end
                        fill = "#64748b"; fontSize = 14.0; fontStyle = "italic"
                        +label
                    }
                } else {
                    // Labels on top
                    text {
                        x = timeScale(i); y = 40.0
                        textAnchor = TextAnchor.middle
                        fill = "#64748b"; fontSize = 14.0; fontStyle = "italic"
                        +label
                    }
                }
            }

            // --- Data Render ---
            props.preds.forEachIndexed { pIndex, row ->

                // Draw Connections
                for (cIndex in 0 until row.size - 1) {
                    val p1 = (row[cIndex]?.dist as? BinaryDistribution)?.yesProb
                    val p2 = (row[cIndex + 1]?.dist as? BinaryDistribution)?.yesProb

                    if (p1 != null && p2 != null) {
                        val delta = p2 - p1
                        val lineColor = updateColor(delta)

                        val (jx1, jy1) = getJitter(pIndex, cIndex)
                        val (jx2, jy2) = getJitter(pIndex, cIndex + 1)
                        val lx1 = toX(p1, cIndex) + jx1
                        val ly1 = toY(p1, cIndex) + jy1
                        val lx2 = toX(p2, cIndex + 1) + jx2
                        val ly2 = toY(p2, cIndex + 1) + jy2

                        // Highlighting logic
                        val isActive = hoverInfo == null || pIndex in activeParticipants
                        val opacity = when {
                            hoverInfo == null -> 0.3
                            isActive -> 0.8
                            else -> 0.05
                        }
                        val strokeW = when {
                            hoverInfo == null -> 8.0
                            isActive -> 9.0
                            else -> 8.0
                        }

                        line {
                            this.x1 = lx1; this.y1 = ly1
                            this.x2 = lx2; this.y2 = ly2
                            stroke = lineColor
                            strokeWidth = strokeW
                            strokeOpacity = opacity.toString()
                            strokeLinecap = StrokeLinecap.round
                            css {
                                transition = Transition(TransitionProperty.all, 0.3.s, TransitionTimingFunction.easeInOut)
                                pointerEvents = None.none
                            }
                        }
                    }
                }

                // Draw Dots
                row.forEachIndexed { cIndex, pred ->
                    val value = (pred?.dist as? BinaryDistribution)?.yesProb
                    if (value != null) {
                        val (jx, jy) = getJitter(pIndex, cIndex)
                        val cxPos = toX(value, cIndex) + jx
                        val cyPos = toY(value, cIndex) + jy

                        val isActive = hoverInfo == null || pIndex in activeParticipants
                        val dotOpacity = when {
                            hoverInfo == null -> 1.0
                            isActive -> 1.0
                            else -> 0.1
                        }

                        circle {
                            cx = cxPos; cy = cyPos
                            r = if (isActive && hoverInfo != null) 6.0 else 4.0
                            fill = "black"
                            fillOpacity = dotOpacity.toString()
                            css {
                                pointerEvents = None.none
                                transition = Transition(TransitionProperty.all, 0.3.s, TransitionTimingFunction.easeInOut)
                            }
                        }
                    }
                }
            }

            // --- Hover highlight ring on active dots ---
            if (hoverInfo != null) {
                for (hit in hoverInfo.hits) {
                    val row = props.preds.getOrNull(hit.participantIndex) ?: continue
                    row.forEachIndexed { cIndex, pred ->
                        val value = (pred?.dist as? BinaryDistribution)?.yesProb ?: return@forEachIndexed
                        val (jx, jy) = getJitter(hit.participantIndex, cIndex)
                        val cxPos = toX(value, cIndex) + jx
                        val cyPos = toY(value, cIndex) + jy
                        circle {
                            cx = cxPos; cy = cyPos; r = 8.0
                            fill = "none"
                            stroke = updateColor(hit.p2 - hit.p1)
                            strokeWidth = 2.0
                            strokeOpacity = "0.6"
                            css { pointerEvents = None.none }
                        }
                    }
                }
            }
        }

        // --- Tooltip (right-side panel, as sibling div of svg) ---
        if (hoverInfo != null && hoverInfo.hits.isNotEmpty()) {
            val displayHits = if (hoverInfo.hits.size > 6) hoverInfo.hits.take(5) else hoverInfo.hits
            val moreCount = hoverInfo.hits.size - displayHits.size

            // Compute tooltip vertical position: align with cursor Y, clamped to graph area
            // Use percentage-based positioning relative to the container
            val tooltipTopPct = ((hoverInfo.svgY / height) * 100).coerceIn(5.0, 85.0)

            div {
                css {
                    position = Position.absolute
                    if (flipped) {
                        // Below the graph area
                        bottom = 4.px
                        left = 50.pct
                        transform = translatex((-50).pct)
                    } else {
                        // Right side
                        right = 8.px
                        top = tooltipTopPct.pct
                        transform = translatey((-50).pct)
                    }
                    backgroundColor = Color("rgba(255, 255, 255, 0.95)")
                    border = Border(1.px, LineStyle.solid, Color("#e2e8f0"))
                    borderRadius = 8.px
                    this.padding = Padding(8.px, 12.px)
                    fontSize = 12.px
                    fontFamily = string("Inter, sans-serif")
                    boxShadow = BoxShadow(0.px, 2.px, 8.px, Color("rgba(0,0,0,0.12)"))
                    zIndex = integer(10)
                    pointerEvents = None.none
                    whiteSpace = WhiteSpace.nowrap
                    minWidth = 100.px
                }
                displayHits.forEach { hit ->
                    div {
                        css {
                            this.padding = Padding(2.px, 0.px)
                            display = Display.flex
                            alignItems = AlignItems.center
                            gap = 6.px
                        }
                        // Color indicator
                        span {
                            css {
                                display = Display.inlineBlock
                                this.width = 8.px
                                this.height = 8.px
                                borderRadius = 50.pct
                                backgroundColor = Color(updateColor(hit.p2 - hit.p1))
                                flexShrink = number(0.0)
                            }
                        }
                        span {
                            +"${formatPercent(hit.p1)} \u2192 ${formatPercent(hit.p2)}"
                        }
                    }
                }
                if (moreCount > 0) {
                    div {
                        css {
                            this.padding = Padding(2.px, 0.px)
                            color = Color("#94a3b8")
                            fontStyle = FontStyle.italic
                        }
                        +"\u2026and $moreCount more"
                    }
                }
            }
        }
    }
})
