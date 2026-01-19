package extensions.update_scatter_plot

import components.ReactPlotly
import components.addShape
import components.buildShapes
import components.ensureTwo
import components.pathToSvg
import components.plotlyVal
import js.objects.unsafeJso as jso
import react.FC
import react.Props
import space.kscience.plotly.layout
import space.kscience.plotly.models.Dash
import space.kscience.plotly.models.Scatter
import space.kscience.plotly.models.Scatter.Companion.invoke
import space.kscience.plotly.models.ScatterMode
import space.kscience.plotly.models.ShapeLayer
import space.kscience.plotly.models.ShapeType
import space.kscience.plotly.models.TickMode
import tools.confido.distributions.BinaryDistribution
import tools.confido.question.Prediction
import tools.confido.utils.List2
import tools.confido.utils.cross
import kotlin.math.abs

enum class UpdateScatterMode {
    DIRECTIONAL,
    EXTREMES,
}


external interface UpdateScatterPlotProps: UpdatePlotProps {
    var mode: UpdateScatterMode
    var diffCoords: Boolean?
}

data class UpdateSize(
    val probRange: kotlin.ranges.ClosedFloatingPointRange<Double>,
    val name: String,
    val colors: List2<String>,
) {
    operator fun contains(diff: Double):Boolean {
        return abs(diff) in probRange && abs(diff) != probRange.endInclusive && diff != 0.0
    }
}

fun hues(hUp: Int, hDown: Int) = List2("hsl($hUp,35,80)", "hsl($hDown,35,80)")

val updateSizes = listOf(
    UpdateSize(0.0..0.05, "Small (0-5%)", List2("#aed581", "#ff8a65")),
    UpdateSize(0.05..0.15, "Medium (5-15%)", List2("#9ccc65", "#ff7043")),
    UpdateSize(0.15..0.3, "Large (15-30%)", List2("#8bc34a", "#ff5722")),
    UpdateSize(0.3..1.0, "XL (>30%)", List2("#7cb342", "#f4511e")),
)

val UpdateScatterPlot = FC<UpdateScatterPlotProps> { props->
    val preds = props.preds.filter { it[0] != null && it[1] != null }.unsafeCast<List<List2<Prediction>>>()
    val before = preds.map { (it[0].dist as BinaryDistribution).yesProb }
    val after = preds.map { (it[1].dist as BinaryDistribution).yesProb }
    val diffCoords = props.diffCoords?: false
    fun transform(xy: List2<Double>) = if (diffCoords) List2(xy.e1, xy.e2-xy.e1 ) else xy
    ReactPlotly {
        config = jso { responsive = true }
        traces = listOf(Scatter {
            console.log(ensureTwo(before).toTypedArray())
            console.log(ensureTwo(before.zip(after) { b,a -> transform(List2(b,a)).e2 }).toTypedArray())
            x.set(ensureTwo(before).toTypedArray())
            y.set(ensureTwo(before.zip(after) { b,a -> transform(List2(b,a)).e2 }).toTypedArray())
            this.mode = ScatterMode.markers
            marker {
                size = 10
                color("blue")
            }
        })
        plotlyInit = {plot->
            plot.layout {
                width = 400
                height = 400
                listOf(xaxis, yaxis).forEach { ax->
                    ax.apply {
                        range(plotlyVal(0), plotlyVal(1))
                        tickmode = TickMode.array
                        tickvals = listOf(0, 0.25, 0.5, 0.75, 1).map(::plotlyVal).toList()
                    }
                    if (diffCoords) {
                        yaxis {
                            range(plotlyVal(-0.3), plotlyVal(0.3))
                            tickvals = (listOf(0.0) + (updateSizes.map{it.probRange.endInclusive} cross listOf(-1.0,1.0)).map{ (x,y)->x*y }.toList()).map(::plotlyVal)
                        }
                    }
                }
                xaxis {
                    title = "Credence before"
                }
                yaxis {
                    title = if (diffCoords) "Credence change" else "Credence after"
                }
                buildShapes {
                    addShape { // frame
                        x0 = plotlyVal(0)
                        x1 = plotlyVal(1)
                        if (diffCoords) {
                            y0 = plotlyVal(-1)
                            y1 = plotlyVal(1)
                        } else {
                            y0 = plotlyVal(0)
                            y1 = plotlyVal(1)
                        }
                    }
                    if (!diffCoords) {
                        addShape { // main diagonal
                            type = ShapeType.line
                            x0 = plotlyVal(0)
                            y0 = plotlyVal(0)
                            x1 = plotlyVal(1)
                            y1 = plotlyVal(1)
                        }
                        props.referenceForecast?.let { ref ->
                            // Vertical reference line
                            addShape {
                                type = ShapeType.line
                                x0 = plotlyVal(ref)
                                y0 = plotlyVal(0)
                                x1 = plotlyVal(ref)
                                y1 = plotlyVal(1)
                                fillcolor("rgba(0, 0, 255, 0.5)")
                                line {
                                    width = 2
                                    dash = Dash.dash
                                }
                            }
                            // Horizontal reference line
                            addShape {
                                type = ShapeType.line
                                x0 = plotlyVal(0)
                                y0 = plotlyVal(ref)
                                x1 = plotlyVal(1)
                                y1 = plotlyVal(ref)
                                fillcolor("rgba(0, 0, 255, 0.5)")
                                line {
                                    width = 2
                                    dash = Dash.dash
                                }
                            }
                        }
                    } else {
                        props.referenceForecast?.let { ref ->
                            // Vertical reference line
                            addShape {
                                type = ShapeType.line
                                x0 = plotlyVal(ref)
                                y0 = plotlyVal(-0.3)
                                x1 = plotlyVal(ref)
                                y1 = plotlyVal(0.3)
                                fillcolor("rgba(0, 0, 255, 0.5)")


                                line {
                                    width = 2
                                    dash = Dash.dash
                                }
                            }
                        }

                    }
                    if (props.mode == UpdateScatterMode.DIRECTIONAL) {
                        var last = 0.0
                        updateSizes.forEach {us->
                            listOf(0,1).forEach { direction->
                                val coords = if (diffCoords)
                                    listOf(
                                        List2(0.0, us.probRange.start),
                                        List2(0.0, us.probRange.endInclusive),
                                        List2(1.0, us.probRange.endInclusive),
                                        List2(1.0, us.probRange.start),
                                    )
                                else
                                    listOf(
                                        List2(0.0, us.probRange.start),
                                        List2(0.0, us.probRange.endInclusive),
                                        List2(1.0 - us.probRange.endInclusive, 1.0),
                                        List2(1.0 - us.probRange.start, 1.0 ),
                                    )
                                val flippedCoords = coords.map{
                                    if (diffCoords) List2(it.e1, -it.e2) else it.reversed() }
                                addShape {
                                    type = ShapeType.path
                                    path = pathToSvg(if (direction == 1) flippedCoords else coords, close=true)
                                    layer = ShapeLayer.below
                                    fillcolor(us.colors[direction] + "cc")
                                    line {
                                        width=0
                                    }
                                }
                            }
                        }
                    }
                }
                margin {
                    this.t = 20
                }
            }
        }
        fixupLayout = {layout ->
            layout.xaxis.mirror = "ticks"
            layout.yaxis.mirror = true
            layout.xaxis.tickformat = ",.0%"
            layout.yaxis.tickformat = ",.0%"
            layout.font = jso{ size = 20 }
        }
    }
}
