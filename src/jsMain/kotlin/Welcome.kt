import csstype.px
import csstype.rgb
import emotion.react.css
import mui.material.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.onChange
import space.kscience.dataforge.values.Value
import space.kscience.plotly.layout
import space.kscience.plotly.models.*
import tools.confido.distributions.NormalDistribution
import tools.confido.distributions.TruncatedNormalDistribution
import kotlin.math.*

external interface WelcomeProps : Props {
    var name: String
}

fun heatmap(): Heatmap {
    val x1 = (5..25).map { (it.toDouble() / 5).pow(2) }
    val y1 = (5..25).map { it.toDouble() / 5 }
    val z1 = mutableListOf<MutableList<Double>>()

    for (i in y1.indices) {
        z1.add(MutableList(x1.size) { 0.0 })
    }

    for (i in y1.indices) {
        for (j in x1.indices) {
            z1[i][j] = sin(x1[i]).pow(10) + cos(10 + y1[j] * x1[i]) * cos(x1[i])
        }
    }

    return Heatmap {
        x.set(x1)
        y.set(y1)
        z.set(z1)
        colorscale = Value.of("Viridis")
    }
}

val Welcome = FC<WelcomeProps> { props ->
    var name by useState(props.name)
    val dist = NormalDistribution(50.0, 1.0)
    val distT = TruncatedNormalDistribution(50.0, 1.0, 0.0, 100.0)

    val xPoints = (0 .. 100).map { it / 100.0 }
    var traces by useState(listOf<Trace>(
        Scatter {
            x.set(xPoints)
            y.set(xPoints.map { distT.icdf(it) })
        }
    ))

    div {
        css {
            padding = 5.px
            backgroundColor = rgb(8, 97, 22)
            color = rgb(56, 246, 137)
        }
        +"Hello, $name"
    }
    ReactPlotly {
        id = "xxx"
        title = name
        this.traces = traces
        plotlyInit = {
            it.layout { this.showlegend = true }
        }
    }

    TextField {
        variant = FormControlVariant.standard
        id = "name-field"
        label = ReactNode("Name")
        value = name
        onChange = {
            name = it.asDynamic().target.value as String
        }
    }

    Button {
        +"Let there be a different quadratic function!"
        variant = ButtonVariant.contained
        onClick = {
            traces = listOf<Trace>(
                heatmap()
            )
        }
    }
}