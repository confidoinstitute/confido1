package components.rooms

import components.AppStateContext
import csstype.Color
import csstype.None.none
import csstype.pct
import csstype.px
import emotion.react.css
import mui.material.*
import mui.system.sx
import react.FC
import react.Props
import react.ReactNode
import react.dom.html.ReactHTML.nav
import react.router.dom.NavLink
import react.useContext

val RoomList = FC<Props> {
    val clientAppState = useContext(AppStateContext)
    val state = clientAppState.state

    Box {
        sx {
            maxWidth = 360.px
            width = 100.pct
        }
        nav {
            mui.material.List {
                for (room in state.rooms) {
                    ListItem {
                        NavLink {
                            to = "/room/${room.id}"
                            css {
                                textDecoration = none
                                color = Color.currentcolor
                            }
                            ListItemButton {
                                ListItemText {
                                    primary = ReactNode(room.name)
                                }
                            }
                        }
                    }
                }
                Divider {}
                ListItem {
                    NavLink {
                        to = "/set_name"
                        css {
                            textDecoration = none
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
}
