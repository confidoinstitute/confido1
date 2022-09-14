package components

import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic
import org.w3c.dom.HTMLDivElement
import react.*
import react.dom.html.ReactHTML.div
import space.kscience.dataforge.meta.MetaSerializer
import space.kscience.dataforge.meta.Scheme
import space.kscience.plotly.*
import space.kscience.plotly.models.Text
import space.kscience.plotly.models.Trace
import utils.*

@OptIn(ExperimentalSerializationApi::class)
private fun Scheme.toDynamic(): dynamic = Json.encodeToDynamic(MetaSerializer, meta)

private fun Collection<Scheme>.toDynamic(): Array<dynamic> = map { it.toDynamic() }.toTypedArray()

external interface PlotlyProps : Props {
    var id: String
    var traces: Collection<Trace>
    var title: String
    var annotations: List<Text>?
    var plotlyInit: ((Plot) -> Unit)?
    var config: dynamic
}

val ReactPlotly = FC<PlotlyProps> {props ->
    val container = useRef<HTMLDivElement>(null)
    val annotations = props.annotations ?: emptyList()
    val plot = Plot().apply {
            layout {
                title = props.title
                this.annotations = annotations
            }

            props.plotlyInit?.invoke(this)
        }

    useEffectOnce {
        val element = container.current ?: error("Div not found")
        console.log("New plot")
        PlotlyJs.newPlot(element, props.traces.toDynamic(), plot.layout.toDynamic(), props.config)

        cleanup {
            PlotlyJs.asDynamic().purge(element)
        }
    }

    useEffect(props.traces, props.annotations) {
        val element = container.current ?: error("Div not found")
        PlotlyJs.react(element, props.traces.toDynamic(), plot.layout.toDynamic(), props.config)
    }
    useEffect(props.title) {
        val element = container.current ?: error("Div not found")
        PlotlyJs.relayout(element, jsObject { this.title = props.title })
    }

    div {
        ref = container
    }
}