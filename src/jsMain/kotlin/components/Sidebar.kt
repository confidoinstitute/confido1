package components

import components.rooms.RoomList
import csstype.Color
import csstype.None
import emotion.react.css
import mui.material.*
import mui.system.Box
import react.*
import react.dom.html.ReactHTML.nav
import react.router.dom.NavLink

external interface SidebarProps : Props {
    var isOpen: Boolean
    var onClose: (() -> Unit)?
}

val Sidebar = FC<SidebarProps> {props ->

    fun navigateClose() {
        props.onClose?.invoke()
    }
    fun navigateClose(a: Any) = navigateClose()
    fun navigateClose(a: Any, b: Any) = navigateClose()

    Box {
        component = nav
        Drawer {
            variant = DrawerVariant.temporary
            anchor = DrawerAnchor.left
            open = props.isOpen
            onClose = ::navigateClose

            RoomList {
                onNavigate = ::navigateClose
            }

            Divider {}

            ListItem {
                NavLink {
                    onClick = ::navigateClose
                    to = "/set_name"
                    css {
                        textDecoration = None.none
                        color = Color.currentcolor
                    }
                    ListItemButton {
                        ListItemText {
                            primary = ReactNode("Change name")
                        }
                    }
                }
            }
        }
    }
}
