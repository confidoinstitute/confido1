package components
import react.*
import space.kscience.plotly.models.Heatmap
import space.kscience.plotly.models.Scatter
import space.kscience.plotly.models.Text
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.utils.binBorders
import kotlin.js.Date
import kotlin.math.PI
import kotlin.math.sin

external interface UpdatesPlotProps : Props {
    var data: Map<Double, ProbabilityDistribution>
    var bins: Int
    var annotations: Map<Double, String>
}

val UpdatesPlot = FC<UpdatesPlotProps> {props ->
    val x = props.data.keys.sorted()
    val y = binBorders(0.0, 1.0, props.bins)
    val z = y.map { y -> props.data.keys.map { x -> sin(x + y * PI) } }


    ReactPlotly {
        traces = listOf(
//            Heatmap {
//                this.x.set(x) //.map { Date(it).toISOString() })
//                this.y.set(y)
//                this.z.set(z)
//            }
            Scatter {
                x(0,1)
                y(0,1)
            }
        )
        //title = "Updates"
//        annotations = props.annotations.entries.map {(ts, text) ->
//            Text {
//                this.position(ts, 1.5)
//                this.text = text
//            }
//        }
    }
}