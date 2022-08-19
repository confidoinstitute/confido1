import kotlinx.browser.document
import kotlinx.dom.clear
import kotlinx.html.dom.append
import react.*
import react.*
import react.dom.html.ReactHTML.div
import space.kscience.plotly.*
import space.kscience.plotly.models.Trace

external interface PlotlyProps : Props {
    var id: String
    var traces: Collection<Trace>
    var title: String
    var plotlyInit: ((Plot) -> Unit)?
}

val ReactPlotly = FC<PlotlyProps> {props ->
    useEffect(props.traces, props.title) {
        val element = document.getElementById(props.id) ?: error("No ID ${props.id}")
        element.append {
            plot {
                traces(props.traces)

                layout {
                    title = props.title
                }

                props.plotlyInit?.invoke(this)
            }
        }
        cleanup {
            element.clear()
        }
    }
    div {
        id = props.id
    }
}