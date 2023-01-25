package components

import csstype.FontSize
import kotlinx.js.jso
import react.FC
import react.Props
import space.kscience.plotly.layout
import space.kscience.plotly.models.Bar
import space.kscience.plotly.models.Pie
import tools.confido.distributions.*
import tools.confido.spaces.Binner
import utils.toIsoDateTime

external interface DistributionPlotProps : Props {
    var distribution: ProbabilityDistribution
    var fontSize: Double?
}

val DistributionPlot = FC<DistributionPlotProps> { props ->
    val config = jso<dynamic> {
        this.responsive = true
    }

    when (val distribution = props.distribution) {
        is BinaryDistribution -> ReactPlotly {
            traces = listOf(
                Pie {
                    labels(listOf("No", "Yes"))
                    values(listOf(distribution.noProb, distribution.yesProb))
                }
            )
            this.config = config
        }
        is ContinuousProbabilityDistribution -> ReactPlotly {
            val discretizedDistribution = when {
                (distribution is DiscretizedContinuousDistribution) -> distribution
                (distribution.space.isInfinite) -> distribution.discretize(
                    Binner(
                        distribution.space.copy(min = distribution.icdf(0.01), max = distribution.icdf(0.99), bins = 1000),
                        1000))
                else -> distribution.discretize(1000)
            }

            traces = listOf(
                Bar {
                    val rangeStarts = discretizedDistribution.binner.binRanges.map { it.start }
                    if (discretizedDistribution.space.representsDays) {
                        x.set(rangeStarts.map{it.toIsoDateTime()})
                    } else {
                        x.set(rangeStarts)
                    }
                    offset = 0
                    hoverinfo = "x"
                    y.set(discretizedDistribution.binProbs)
                }
            )
            props.fontSize?.let {fontSize ->
                fixupLayout = {
                    it.font = jso {
                        size = 24
                    }
                }
            }
            plotlyInit = { plot ->
                plot.layout {
                    bargap = 0
                    autosize = true
                    yaxis {
                        visible = false
                        showline = false
                    }
                }
            }

            this.config = config
        }
    }
}