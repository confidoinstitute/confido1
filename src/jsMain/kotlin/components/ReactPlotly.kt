package components

import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic
import dom.html.HTMLDivElement
import kotlinx.js.jso
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

    /**
     * This can be used to set layout properties not supported by the PlotlyKt scheme.
     */
    var fixupLayout: ((dynamic) -> Unit)?
}

val ReactPlotly = FC<PlotlyProps> {props ->
    val container = useRef<HTMLDivElement>(null)

    // Note that the plotly.kt library does not yet support dom.html.Element, which
    // is why use unsafe casts. These should be removed once the library is updated.

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
        val dynLayout = plot.layout.toDynamic()
        props.fixupLayout?.invoke(dynLayout)
        console.log(dynLayout)
        PlotlyJs.newPlot(element.unsafeCast<org.w3c.dom.Element>(), props.traces.toDynamic(), dynLayout, props.config)

        cleanup {
            PlotlyJs.asDynamic().purge(element)
        }
    }

    useEffect(props.traces, props.annotations) {
        val element = container.current ?: error("Div not found")
        PlotlyJs.react(element.unsafeCast<org.w3c.dom.Element>(), props.traces.toDynamic(), plot.layout.toDynamic(), props.config)
    }
    useEffect(props.title) {
        val element = container.current ?: error("Div not found")
        PlotlyJs.relayout(element.unsafeCast<org.w3c.dom.Element>(), jso<dynamic> { this.title = props.title })
    }

    div {
        ref = container
    }
}