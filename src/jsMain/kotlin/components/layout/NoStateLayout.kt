package components.layout

import csstype.*
import mui.material.CircularProgress
import mui.material.CircularProgressColor
import mui.system.*
import react.*
import utils.themed

val NoStateLayout = FC<Props> {
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
