package components.layout

import components.AppStateContext
import components.ListItemNavigation
import components.rooms.RoomList
import csstype.*
import icons.LogoutIcon
import icons.SettingsIcon
import mui.material.*
import mui.system.Breakpoint
import mui.system.responsive
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.nav
import react.router.useNavigate
import utils.themed

val sidebarWidth = 240.px
val permanentBreakpoint = Breakpoint.md

external interface SidebarProps : Props {
    var permanent: Boolean
    var isOpen: Boolean
    var onClose: (() -> Unit)?
}

val Sidebar = FC<SidebarProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val navigate = useNavigate()

    val navigateClose = useMemo(props.onClose) {
        fun(vararg args: Any) {
            props.onClose?.invoke()
        }
    }

    fun displayType(sm: Boolean): Display =
        if (props.permanent xor sm) None.none else Display.block

    mui.system.Box {
        key = "mainbox"
        component = nav
        sx {
            width = responsive(permanentBreakpoint to sidebarWidth)
            flexShrink = responsive(permanentBreakpoint to number(0.0))
        }
        Drawer {
            key = "drawer"
            sx {
                display = responsive(Breakpoint.xs to displayType(false), permanentBreakpoint to displayType(true))
                "& .MuiDrawer-paper" {
                    boxSizing = BoxSizing.borderBox
                    width = sidebarWidth
                    zIndex = responsive(permanentBreakpoint to number(0.0))
                    boxShadow = responsive(permanentBreakpoint to themed(4))
                }
            }
            if (props.permanent) {
                variant = DrawerVariant.permanent
            } else {
                variant = DrawerVariant.temporary
                this.onClose = {_, _ -> props.onClose?.invoke()}
            }
            anchor = DrawerAnchor.left
            open = props.isOpen || props.permanent
            Toolbar {}

            RoomList {
                key = "room_list"
                newRoomEnabled = appState.isFullUser
                onNavigate = navigateClose
            }
        }
    }
}
