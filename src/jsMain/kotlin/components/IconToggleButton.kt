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
    var on: Boolean?
    var defaultOn: Boolean?
    var onChange: ((Boolean) -> Unit)?
}

val IconToggleButton = FC<IconToggleButtonProps> {props->
    // FIXME: tooltip shows only after clicking on the button. Can be worked arround
    // by wrapping use in span{}
    var state by useState(props.defaultOn ?: false)
    val effectiveState = props.on ?: state
    IconButton {
        +props.except("onIcon", "offIcon", "on", "defaultOn", "onChange") // needed to show tooltips
        ariaPressed = if (effectiveState) AriaPressed.`true` else AriaPressed.`false`
        +(if (effectiveState) props.onIcon else props.offIcon)
        onClick = {
            val newState = !effectiveState
            if (props.on==null) state = newState
            props.onChange?.invoke(newState)
        }
    }
}
