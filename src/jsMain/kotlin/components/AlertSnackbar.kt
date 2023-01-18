package components

import csstype.pct
import dom.html.HTMLDivElement
import kotlinx.js.delete
import mui.material.*
import mui.system.sx
import react.*

internal val AlertForSnackbar = ForwardRef<HTMLDivElement, AlertProps> { props, forwardedRef ->
    Alert {
        ref = forwardedRef
        elevation = 6
        variant = AlertVariant.filled
        +props
    }
}

external interface AlertSnackbarProps : SnackbarProps {
    var severity: AlertColor
}

val AlertSnackbar = ForwardRef<HTMLDivElement, AlertSnackbarProps> {props, forwardedRef ->
    Snackbar {
        ref = forwardedRef
        +props
        AlertForSnackbar {
            severity = props.severity
            children = props.children
            sx {
                width = 100.pct
            }
        }
    }
}