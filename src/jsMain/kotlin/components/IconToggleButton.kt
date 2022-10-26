package components

import mui.material.IconButton
import react.FC
import react.Props
import react.ReactNode
import react.dom.aria.AriaPressed
import react.dom.aria.ariaPressed
import react.dom.html.ReactHTML.span
import react.useState
import utils.except

external interface IconToggleButtonProps : Props {
    var onIcon: ReactNode
    var offIcon: ReactNode
    var on: Boolean
    var onChange: ((Boolean) -> Unit)?
}

val StatelessIconToggleButton = FC<IconToggleButtonProps> {props->
    // FIXME: tooltip shows only after clicking on the button. Can be worked arround
    // by wrapping use in span{}
    IconButton {
        +props.except("onIcon", "offIcon", "on", "onChange") // needed to show tooltips
        ariaPressed = if (props.on) AriaPressed.`true` else AriaPressed.`false`
        +(if (props.on) props.onIcon else props.offIcon)
        onClick = { props.onChange?.invoke(!props.on) }
    }
}

val StatefulIconToggleButton = FC<IconToggleButtonProps> {props->
    var state by useState(props.on)
    StatelessIconToggleButton {
        +props.except("on", "onChange")
        on = state
        onChange = { state = !state; props.onChange?.invoke(state) }
    }
}