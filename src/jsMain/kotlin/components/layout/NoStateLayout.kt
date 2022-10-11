package components.layout

import csstype.*
import mui.material.*
import mui.system.*
import mui.system.Box
import react.*
import utils.themed

external interface NoStateLayoutProps : Props {
    var stale: Boolean
}

val NoStateLayout = FC<NoStateLayoutProps> {props ->
    Collapse {
        this.`in` = props.stale
        Alert {
            severity = AlertColor.error
            +"The server cannot be reached. If this persists, please contact the administrators."
        }
    }
    Box {
        sx {
            position = Position.absolute
            left = 50.pct
            top = 50.pct
            transform = translate((-50).pct, (-50).pct)
        }
        CircularProgress {
            color = CircularProgressColor.secondary
            size = 120
        }
    }
}
