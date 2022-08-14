import csstype.px
import csstype.rgb
import react.FC
import react.Props
import emotion.react.css
import mui.material.*
import org.w3c.dom.HTMLInputElement
import react.ReactNode
import react.dom.events.ChangeEvent
import react.dom.html.InputType
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.onChange
import react.useState

external interface WelcomeProps : Props {
    var name: String
}

val Welcome = FC<WelcomeProps> { props ->
    var name by useState(props.name)
    div {
        css {
            padding = 5.px
            backgroundColor = rgb(8, 97, 22)
            color = rgb(56, 246, 137)
        }
        +"Hello, $name"
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
}