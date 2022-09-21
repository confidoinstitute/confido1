package components
import react.*
import space.kscience.dataforge.values.Value
import space.kscience.dataforge.values.asValue
import space.kscience.plotly.layout
import space.kscience.plotly.models.Bar
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.utils.binBorders
import tools.confido.utils.binRanges
import utils.jsObject

data class ConfidenceColor(
    val p: Double,
    val color: Value,
)

external interface DistributionPlotProps : Props {
    var id: String
    var distribution: ProbabilityDistribution
    var min: Double
    var max: Double
    var bins: Int
    var confidences: List<ConfidenceColor>
    var outsideColor: Value?
}

val DistributionPlot = FC<DistributionPlotProps> {props ->
    val xTicks = useMemo(props.min, props.max, props.bins) {
        binBorders(props.min, props.max, props.bins)
    }
    val ranges = useMemo(props.min, props.max, props.bins) {
        binRanges(props.min, props.max, props.bins)
    }

    val yTicks = ranges.map {(a, b) -> props.distribution.probabilityBetween(a, b)}

    val confidenceIntervals = props.confidences.map {
        Pair(props.distribution.confidenceInterval(1 - it.p), it.color)
    }
    fun barColor(x: Double) = confidenceIntervals.find {
            val (start, end) = it.first
            (x in start..end)
        }?.second ?: props.outsideColor ?: Value.of("")

    val colorTicks = xTicks.map { barColor(it) }


    ReactPlotly {
        id = props.id
        annotations = listOf()

        traces = listOf(
            Bar {
                x.set(xTicks)
                y.set(yTicks)
                marker {
                    colors(colorTicks)
                }
            }
        )

        plotlyInit = { plot ->
            plot.layout {
                margin {
                    l = 0
                    r = 0
                    b = 25
                    t = 0
                }
                xaxis {
                    this.showline = false
                    this.visible = false
                    this.range(props.min.asValue(), props.max.asValue())
                }
                yaxis {
                    this.showline = false
                    this.visible = false
                }
                height = 100
                showlegend = false
                bargap = 0
            }
        }
        config = jsObject {
            staticPlot = true
            responsive = true
        }
    }
}