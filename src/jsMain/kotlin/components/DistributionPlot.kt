package components

import csstype.FontSize
import io.ktor.util.reflect.*
import kotlinx.js.jso
import react.FC
import react.Props
import space.kscience.plotly.layout
import space.kscience.plotly.models.*
import tools.confido.distributions.*
import tools.confido.spaces.Binner
import utils.toIsoDateTime

external interface DistributionPlotProps : Props {
    var distribution: ProbabilityDistribution
    var fontSize: Double?
    var meanLine: Boolean?
    var resolutionLine: Double?
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
                    marker.color("#3055F1")
                }
            )
            fixupLayout = {
                props.fontSize?.let {fontSize ->
                    it.font = jso {
                        size = fontSize
                    }
                }
                (it.annotations as Array<dynamic>).forEach { it.arrowhead = 0 }
            }
            val maxProb = discretizedDistribution.binProbs.max()
            plotlyInit = { plot ->
                plot.layout {
                    bargap = 0
                    autosize = true
                    fun <T> ensureTwo(lst: List<T>) = if (lst.size == 1) lst + lst else lst
                    val annot = mutableListOf<Text>()
                    val shp = mutableListOf<Shape>()
                    val nlines = mutableMapOf<XAnchor, Int>()

                    fun addLine(x: Double, clr: String, text: String) {
                        annot.add(Text().apply {
                            this.x = plotlyVal(x)
                            y = plotlyVal(maxProb)
                            val lineNo = annot.size
                            this.ay = plotlyVal( - lineNo * (props.fontSize?:24.0)*1.25)
                            this.ax = plotlyVal(0) //plotlyVal(8 * (if (xAnchor == XAnchor.right) -1 else 1))
                            this.text = text
                            font { color(clr) }
                            arrowcolor(clr)
                            this.yanchor = YAnchor.bottom
                            val xrel = ((x - distribution.space.min) / distribution.space.size )
                            this.xanchor = if (xrel < 1.0/3.0) XAnchor.left
                                            else if (xrel > 2.0/3.0) XAnchor.right
                                            else XAnchor.center

                            showarrow = true
                        })

                        shp.add(Shape().apply {
                            x0 = plotlyVal(x)
                            x1 = plotlyVal(x)
                            y0 = plotlyVal(0)
                            y1 = plotlyVal(maxProb)
                            line {
                                color(clr)
                            }
                        })

                    }

                    if (props.meanLine ?: true) {
                        addLine(distribution.mean, "red", "mean estimate (${distribution.space.formatValue(distribution.mean)})")
                    }
                    props.resolutionLine?.let { resolution ->
                        addLine(resolution, "#2196f3", "correct answer (${distribution.space.formatValue(resolution)})")
                    }
                    // due to bug in plotly.kt, it does not work if the length of the list is 1
                    // in that case, simply duplicate it (it will be drawn twice over and look the same)
                    annotations = ensureTwo(annot)
                    shapes = ensureTwo(shp)
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
