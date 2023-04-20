package components.redesign

import components.AppStateContext
import components.redesign.basic.Stack
import components.redesign.rooms.RoomList
import csstype.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.aside
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img

external interface SidebarProps : PropsWithChildren {
    var open: Boolean?
}

val Sidebar = FC<SidebarProps> { props ->
    val (appState, stale) = useContext(AppStateContext)

    Stack {
        direction = FlexDirection.row
        css {
            flexGrow = number(1.0)
        }
        aside {
            css {
                backgroundColor = Color("#FFFFFF")

                width = if (props.open == true) {
                    280.px
                } else {
                    0.px
                }
                transition = 0.5.s
                zIndex = integer(100)
            }

            Stack {
                css {
                    gap = 30.px
                    padding = 15.px
                    position = Position.sticky
                    top = 0.px
                    flexShrink = number(0.0)
                    width = Globals.inherit
                }
                SidebarHeader {}
                appState.session.user?.let { user ->
                    WorkspaceFrame {
                        this.user = user
                    }
                }
                RoomList {
                    small = true
                }
            }
        }
        div {
            css {
                flexGrow = number(1.0)
            }
            +props.children
        }
    }
}

private val SidebarHeader = FC<Props> {
    img {
        css {
            height = 40.px
        }
        src = "/static/sidebar_logo.svg"
    }
}

