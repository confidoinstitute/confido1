package extensions.update_scatter_plot

import components.redesign.basic.PropsWithElementSize
import components.redesign.basic.elementSizeWrapper
import emotion.react.css
import react.FC
import react.dom.html.ReactHTML.div
import react.dom.svg.ReactSVG.circle
import react.dom.svg.ReactSVG.g
import react.dom.svg.ReactSVG.line
import react.dom.svg.ReactSVG.svg
import react.dom.svg.ReactSVG.text
import react.dom.svg.StrokeLinecap
import react.dom.svg.TextAnchor
import react.useState
import tools.confido.distributions.BinaryDistribution
import web.cssom.Border
import web.cssom.Color
import web.cssom.Cursor
import web.cssom.Display
import web.cssom.JustifyContent
import web.cssom.LineStyle
import web.cssom.NamedColor
import web.cssom.None
import web.cssom.Overflow
import web.cssom.Transition
import web.cssom.TransitionProperty
import web.cssom.TransitionTimingFunction
import web.cssom.px
import web.cssom.s
import kotlin.math.abs

external interface UpdateSlopeProps : PropsWithElementSize, UpdatePlotProps {
    var width: Double?
    var height: Double?
}

// State for hover interactions
data class HoverState(
    val value: Double,
    val columnIndex: Int
)

val UpdateSlopeChart = elementSizeWrapper(FC<UpdateSlopeProps> { props ->
    // State
    val (hoverInfo, setHoverInfo) = useState<HoverState?>(null)

    val numColumns = props.preds.firstOrNull()?.size ?: 2

    val width = props.width ?: numColumns * 200.0
    val height = props.height ?: 600.0

    // Layout Constants
    val padding = object {
        val top = 60.0
        val bottom = 60.0
        val left = 80.0
        val right = 80.0
    }

    val graphHeight = height - padding.top - padding.bottom - 2
    val graphWidth = width - padding.left - padding.right -2


    // Helper: Map 0.0-1.0 to Y-coordinate (Inverted)
    fun yScale(value: Double): Double {
        return padding.top + graphHeight - (value * graphHeight)
    }

    // Helper: Calculate X-coordinate for a specific column index
    fun xScale(colIndex: Int): Double {
        if (numColumns <= 1) return (width / 2).toDouble()
        val step = graphWidth / (numColumns - 1)
        return padding.left + (step * colIndex)
    }

    // Deterministic Jitter: spread points slightly based on participant index
    // so they don't perfectly overlap.
    fun getJitter(participantId: Int, colIndex: Int): Double {
        // Simple distinct hash for visual offsets
        val base = (participantId * 31 + colIndex * 17) % 100
        // Spread approx +/- 10px
        return (base - 50) / 50.0 * 10.0
    }

    div {
        css {
            display = Display.flex
            justifyContent = JustifyContent.center
            overflow = Overflow.hidden
            backgroundColor = NamedColor.white
            borderRadius = 12.px
            border = Border(1.px, LineStyle.solid, Color("#e2e8f0"))
        }

        svg {
            viewBox = "0 0 $width $height"
            this.width = width.toDouble()
            this.height = height.toDouble()
            style = js("({ fontFamily: 'Inter, sans-serif' })")

            // --- 1. Grid Lines (25%, 50%, 75%) ---
            listOf(0.25, 0.50, 0.75).forEach { value ->
                g {
                    key = value.toString()
                    val yPos = yScale(value)

                    line {
                        x1 = padding.left - 20
                        y1 = yPos
                        x2 = width - padding.right + 20
                        y2 = yPos
                        stroke = "#e2e8f0"
                        strokeWidth = 1.0
                        strokeDasharray = "4 4"
                    }
                    text {
                        x = padding.left - 30
                        y = yPos
                        dy = 4.0
                        textAnchor = TextAnchor.end
                        fill = "#94a3b8"
                        fontSize = 12.0
                        fontWeight = "500"
                        +"${(value * 100).toInt()}%"
                    }
                }
            }

            // --- 2. Top/Bottom Frame Borders ---
            line {
                x1 = padding.left - 20
                y1 = padding.top
                x2 = width - padding.right + 20
                y2 = padding.top
                stroke = "#cbd5e1"
                strokeWidth = 1.0
            }
            line {
                x1 = padding.left - 20
                y1 = height - padding.bottom
                x2 = width - padding.right + 20
                y2 = height - padding.bottom
                stroke = "#cbd5e1"
                strokeWidth = 1.0
            }

            // --- 3. Axis Labels (Yes/No) ---
            text {
                x = padding.left - 30
                y = yScale(1.0)
                dy = 4.0
                textAnchor = TextAnchor.end
                fill = "#00cc2e"
                fontWeight = "600"
                fontSize = 14.0
                +"Yes"
            }
            text {
                x = padding.left - 30
                y = yScale(0.0)
                dy = 4.0
                textAnchor = TextAnchor.end
                fill = "#ff5555"
                fontWeight = "600"
                fontSize = 14.0
                +"No"
            }

            // --- 4. Column Labels ("Time 1", etc) ---
            for (i in 0 until numColumns) {
                text {
                    x = xScale(i)
                    y = 40.0
                    textAnchor = TextAnchor.middle
                    fill = "#64748b"
                    fontSize = 14.0
                    fontStyle = "italic"
                    +(props.timepointLabels?.getOrNull(i) ?: if (numColumns == 2) listOf("Before", "After")[i] else "Time ${i + 1}")
                }
            }

            // --- 5. Data Render ---
            props.preds.forEachIndexed { pIndex, row ->

                // Draw Connections
                for (cIndex in 0 until row.size - 1) {
                    val p1 = (row[cIndex]?.dist as? BinaryDistribution)?.yesProb
                    val p2 = (row[cIndex + 1]?.dist as? BinaryDistribution)?.yesProb

                    if (p1 != null && p2 != null) {
                        val isIncreasing = p2 > p1
                        val lineColor = if (isIncreasing) "#00cc2e" else "#ff5555"

                        val x1 = xScale(cIndex) + getJitter(pIndex, cIndex)
                        val x2 = xScale(cIndex + 1) + getJitter(pIndex, cIndex + 1)
                        val y1 = yScale(p1)
                        val y2 = yScale(p2)

                        // Interaction Logic
                        var isActive = true
                        var opacity = 0.3
                        var strokeW = 8.0

                        if (hoverInfo != null) {
                            // Logic: If highlighting, check proximity to the node in the column hovered
                            // We compare the value of this participant at the hovered column
                            val valAtHoverCol = (row.getOrNull(hoverInfo.columnIndex)?.dist as? BinaryDistribution)?.yesProb

                            if (valAtHoverCol != null) {
                                val diff = abs(valAtHoverCol - hoverInfo.value)
                                isActive = diff <= 0.05
                            } else {
                                isActive = false
                            }

                            opacity = if (isActive) 0.8 else 0.05
                            strokeW = if (isActive) 9.0 else 8.0
                        }

                        // SVG Line
                        line {
                            this.x1 = x1
                            this.y1 = y1
                            this.x2 = x2
                            this.y2 = y2
                            stroke = lineColor
                            strokeWidth = strokeW
                            strokeOpacity = opacity.toString()
                            strokeLinecap = StrokeLinecap.round
                            css {
                                transition = Transition(TransitionProperty.all, 0.3.s, TransitionTimingFunction.easeInOut)
                            }
                        }
                    }
                }

                // Draw Dots
                row.forEachIndexed { cIndex, pred ->
                    val value = (pred?.dist as? BinaryDistribution)?.yesProb
                    if (value != null) {
                        val cxPos = xScale(cIndex) + getJitter(pIndex, cIndex)
                        val cyPos = yScale(value)

                        // Check if this specific dot matches hover condition
                        val isHoverActive = hoverInfo != null &&
                                abs(value - hoverInfo.value) <= 0.05 &&
                                hoverInfo.columnIndex == cIndex

                        g {
                            // Invisible Hit Target for easier hovering
                            circle {
                                cx = cxPos
                                cy = cyPos
                                r = 8.0
                                fill = "transparent"
                                css { cursor = Cursor.pointer }
                                onMouseEnter = {
                                    setHoverInfo(HoverState(value, cIndex))
                                }
                                onMouseLeave = {
                                    setHoverInfo(null)
                                }
                            }

                            // Visible Dot
                            circle {
                                cx = cxPos
                                cy = cyPos
                                r = if (isHoverActive) 6.0 else 4.0
                                fill = "black"
                                css { pointerEvents = None.none }
                            }
                        }
                    }
                }
            }
        }
    }
})
