package components

import dom.html.HTMLButtonElement
import mui.material.IconButton
import react.*
import react.dom.aria.AriaPressed
import react.dom.aria.ariaPressed
import react.dom.html.ReactHTML.span
import utils.except

external interface IconToggleButtonProps : PropsWithRef<HTMLButtonElement> {
    var onIcon: ReactNode
    var offIcon: ReactNode
    var disabled: Boolean
    var on: Boolean?
    var defaultOn: Boolean?
    var onChange: ((Boolean) -> Unit)?
}

/**
 * Button that changes its icon according to its inner state (if `on` is not provided) or controlled state
 */
val IconToggleButton = ForwardRef<HTMLButtonElement, IconToggleButtonProps> {props, forwardedRef ->
    var state by useState(props.defaultOn ?: false)
    val effectiveState = props.on ?: state
    IconButton {
        this.ref = forwardedRef
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
