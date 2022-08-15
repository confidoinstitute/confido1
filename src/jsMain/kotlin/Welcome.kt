import csstype.px
import csstype.rgb
import emotion.react.css
import mui.material.*
import org.w3c.dom.HTMLInputElement
import react.*
import react.dom.events.ChangeEvent
import react.dom.html.InputType
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.onChange
import space.kscience.plotly.Plotly.plot
import space.kscience.plotly.plot
import space.kscience.plotly.layout
import space.kscience.plotly.models.*
import space.kscience.plotly.scatter

external interface WelcomeProps : Props {
    var name: String
}

val Welcome = FC<WelcomeProps> { props ->
    var name by useState(props.name)

    var traces by useState(listOf(
        Scatter {
            x(1,2,3,4,5)
            y(1,3,6,10,15)
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
        title = name
        this.traces = traces
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
            traces = listOf(
                Scatter {
                    x(1,2,3,4,5)
                    y(1,4,9,16,25)
                }
            )
        }
    }
}