package components.layout

import components.AppStateContext
import csstype.None
import csstype.number
import icons.MenuIcon
import mui.material.*
import mui.system.responsive
import mui.system.sx
import react.FC
import react.Props
import react.ReactNode
import react.useContext
import utils.themed

external interface RootAppBarProps : Props {
    var hasDrawer: Boolean
    var onDrawerOpen: (() -> Unit)?
}

val RootAppBar = FC<RootAppBarProps> { props ->
    val stale = useContext(AppStateContext).stale

    AppBar {
        position = AppBarPosition.fixed
        Toolbar {
            if (props.hasDrawer) {
                IconButton {
                    sx {
                        display = responsive(permanentBreakpoint to None.none)
                        marginRight = themed(2)
                    }
                    color = IconButtonColor.inherit
                    onClick = { props.onDrawerOpen?.invoke() }
                    MenuIcon()
                }
            }
            Typography {
                sx {
                    flexGrow = number(1.0)
                }
                +"Confido"
            }
            if (stale) {
                Chip {
                    this.color = ChipColor.error
                    this.label = ReactNode("Disconnected")
                }
            }
        }
    }
}