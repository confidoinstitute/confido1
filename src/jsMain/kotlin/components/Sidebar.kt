package components

import components.rooms.RoomList
import csstype.*
import emotion.react.css
import mui.material.*
import mui.system.Box
import mui.system.Breakpoint
import mui.system.responsive
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.nav
import react.router.dom.NavLink

val sidebarWidth = 240.px
external interface SidebarProps : Props {
    var permanent: Boolean
    var isOpen: Boolean
    var onClose: (() -> Unit)?
}

val Sidebar = FC<SidebarProps> {props ->

    fun navigateClose() {
        props.onClose?.invoke()
    }
    fun navigateClose(a: Any) = navigateClose()
    fun navigateClose(a: Any, b: Any) = navigateClose()

    fun displayType(sm: Boolean): Display =
        if (props.permanent xor sm) None.none else Display.block

    Drawer {
        sx {
            display = responsive(Breakpoint.xs to displayType(false), permanentBreakpoint to displayType(true))
            "& .MuiDrawer-paper" {
                boxSizing = BoxSizing.borderBox
                width = sidebarWidth
                zIndex = responsive(permanentBreakpoint to number(0.0))
            }
        }
        if (props.permanent) {
            variant = DrawerVariant.permanent
        } else {
            variant = DrawerVariant.temporary
            onClose = ::navigateClose
        }
        anchor = DrawerAnchor.left
        open = props.isOpen || props.permanent
        Toolbar {}

        RoomList {
            onNavigate = ::navigateClose
        }

        Divider {}

        List {
            dense = true
            ListItemNavigation {
                to = "/set_name"
                this.onNavigate = ::navigateClose
                ListItemText {
                    primary = ReactNode("Change name")
                }
            }
        }
    }
}
