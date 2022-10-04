package components.questions
import hooks.useElementSize
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import react.*
import react.dom.html.ReactHTML.canvas
import react.dom.html.ReactHTML.div
import space.kscience.dataforge.values.Value
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.utils.binRanges

data class ConfidenceColor(
    val p: Double,
    val color: Value,
)

external interface DistributionPlotProps : Props {
    var distribution: ProbabilityDistribution
    var min: Double
    var max: Double
    var confidences: List<ConfidenceColor>
    var outsideColor: Value?
    var visible: Boolean
    var height: Double?
}

val DistributionPlot = FC<DistributionPlotProps> { props ->
    val elementSize = useElementSize<HTMLDivElement>()
    val bins = elementSize.width.toInt()

    val height = props.height ?: 100.0

    val ranges = useMemo(props.min, props.max, bins) {
        binRanges(props.min, props.max, bins)
    }

    val confidenceIntervals = props.confidences.map {
        Pair(props.distribution.confidenceInterval(1 - it.p), it.color)
    }
    fun barColor(x: Double) = confidenceIntervals.find {
            val (start, end) = it.first
            (x in start..end)
        }?.second ?: props.outsideColor ?: Value.of("")

    val yTicks = ranges.map { (a, b) -> props.distribution.probabilityBetween(a, b) to barColor((b + a) / 2) }

    val canvas = useRef<HTMLCanvasElement>()
    useEffect(yTicks, elementSize.width, elementSize.height, props.visible) {
        val context = canvas.current?.getContext("2d") as? CanvasRenderingContext2D
        val scale = height / (yTicks.maxByOrNull { (value, _) -> value }?.first ?: 1.0)
        context?.apply {
            clearRect(0.0, 0.0, elementSize.width, height)
            if (props.visible)
            yTicks.mapIndexed {index, yTick ->
                beginPath()
                moveTo(index.toDouble() + 0.5, height)
                lineTo(index.toDouble() + 0.5, height - yTick.first * scale)
                strokeStyle = yTick.second.toString()
                stroke()
            }
        }
    }

    div {
        ref = elementSize.ref
        canvas {
            width = elementSize.width
            this.height = height
            ref = canvas
        }
    }
}