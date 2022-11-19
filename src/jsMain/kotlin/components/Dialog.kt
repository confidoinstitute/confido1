package components

import csstype.*
import icons.*
import mui.material.*
import mui.system.sx
import react.*

external interface DialogTitleWithCloseButtonProps : DialogTitleProps {
    var onClose: (() -> Unit)?
}

/**
 * DialogTitle with a close button in top right.
 * Make sure to set `onClose` in props for the button to appear.
 */
val DialogTitleWithCloseButton = FC<DialogTitleWithCloseButtonProps> { props ->
    DialogTitle {
        +props
        +props.children
        val onClose = props.onClose
        if (onClose != null) {
            IconButton {
                sx {
                    position = Position.absolute
                    right = 8.px
                    top = 8.px
                }
                onClick = { onClose() }
                CloseIcon {}
            }
        }
    }
}
