package components.questions
import hooks.useElementSize
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import react.*
import react.dom.html.ReactHTML.canvas
import react.dom.html.ReactHTML.div
import space.kscience.dataforge.values.Value
import tools.confido.distributions.ContinuousProbabilityDistribution

data class ConfidenceColor(
    val p: Double,
    val color: Value,
)

external interface SimpleCondDistPlotProps : Props {
    var dist: ContinuousProbabilityDistribution
    var preferredCICenter: Double?
    var confidences: List<ConfidenceColor>
    var outsideColor: Value?
    var visible: Boolean
    var height: Double?
}

// Simple non-interactive plot using canvas for live distribution preview above slider
val SimpleContDistPlot = FC<SimpleCondDistPlotProps> { props ->
    val elementSize = useElementSize<HTMLDivElement>()
    val bins = elementSize.width.toInt()

    val height = props.height ?: 100.0

    val discretized = useMemo(props.dist, bins) {
        props.dist.discretize(bins)
    }

    val confidenceIntervals = props.confidences.map {
        Pair(props.dist.confidenceInterval(it.p, props.preferredCICenter ?: props.dist.preferredCICenter), it.color)
    }
    fun barColor(x: Double) = confidenceIntervals.find {
            x in it.first
        }?.second ?: props.outsideColor ?: Value.of("")

    val yTicks = (0 until bins).map { bin -> discretized.binProbs[bin] to barColor(discretized.binner.binMidpoints[bin]) }

    val canvas = useRef<HTMLCanvasElement>()
    useLayoutEffect(yTicks, elementSize.width, elementSize.height, props.visible) {
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