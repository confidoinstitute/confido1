package components

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic
import org.w3c.dom.HTMLDivElement
import react.*
import react.dom.html.ReactHTML.div
import space.kscience.dataforge.meta.MetaSerializer
import space.kscience.dataforge.meta.Scheme
import space.kscience.plotly.*
import space.kscience.plotly.models.Trace
import utils.*

@OptIn(ExperimentalSerializationApi::class)
private fun Scheme.toDynamic(): dynamic = Json.encodeToDynamic(MetaSerializer, meta)

private fun Collection<Scheme>.toDynamic(): Array<dynamic> = map { it.toDynamic() }.toTypedArray()

external interface PlotlyProps : Props {
    var id: String
    var traces: Collection<Trace>
    var title: String
    var plotlyInit: ((Plot) -> Unit)?
    var config: dynamic
}

val ReactPlotly = FC<PlotlyProps> {props ->
    val container = useRef<HTMLDivElement>(null)
    val plot by useState {
        console.log("Plot created")
        Plot().apply {
            layout {
                title = props.title
            }

            props.plotlyInit?.invoke(this)
        }
    }

    useEffectOnce {
        val element = container.current ?: error("Div not found")
        PlotlyJs.newPlot(element, props.traces.toDynamic(), plot.layout.toDynamic(), props.config)

        cleanup {
            PlotlyJs.asDynamic().purge(element)
        }
    }

    useEffect(props.traces) {
        val element = container.current ?: error("Div not found")
        console.log("Effect used for traces")

        PlotlyJs.react(element, props.traces.toDynamic(), plot.layout.toDynamic(), props.config)
    }
    useEffect(props.title) {
        val element = container.current ?: error("Div not found")
        console.log("Effect used for title")

        PlotlyJs.relayout(element, jsObject { this.title = props.title })
    }

    div {
        ref = container
    }
}