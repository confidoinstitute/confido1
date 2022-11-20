package components

import csstype.*
import icons.*
import mui.material.*
import mui.system.sx
import react.*

external interface DialogCloseButtonProps : Props {
    var onClose: (() -> Unit)?
}

/**
 * Close button with a close button in top right, to be used within [DialogTitle].
 *
 * Make sure to set [DialogCloseButtonProps.onClose] in props for the button to appear.
 */
val DialogCloseButton = FC<DialogCloseButtonProps> { props ->
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
