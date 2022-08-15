import kotlinx.browser.document
import kotlinx.dom.clear
import kotlinx.html.dom.append
import react.*
import react.*
import react.dom.html.ReactHTML.div
import space.kscience.plotly.*
import space.kscience.plotly.models.Trace

external interface PlotlyProps : Props {
    var traces: Collection<Trace>
    var title: String
}

val ReactPlotly = FC<PlotlyProps> {props ->
    useEffect(props.title, props.traces) {
        val element = document.getElementById("xxx") ?: error("No ID xxx")
        element.append {
            plot {
                traces(props.traces)
                layout {
                    title = props.title
                }
            }
        }
        cleanup {
            element.clear()
        }
    }
    div {
        id = "xxx"
    }
}