package components.redesign

import components.AppStateContext
import components.ClientAppState
import components.redesign.basic.LinkUnstyled
import components.redesign.basic.Stack
import components.redesign.feedback.FeedbackMenuItem
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.rooms.RoomList
import csstype.*
import emotion.react.Global
import emotion.react.css
import emotion.react.styles
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.aside
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.router.Outlet
import react.router.dom.Link

external interface SidebarProps : PropsWithChildren {
    var open: Boolean?
}

class SidebarState internal constructor(
    val isOpen: Boolean,
    val isAvailable: Boolean,
    val setState: (Boolean) -> Unit,
) {
    fun open() {
        setState(true)
    }

    fun close() {
        setState(false)
    }

    fun toggle() {
        setState(!isOpen)
    }
}

val SidebarContext = createContext<SidebarState>()

val SidebarLayout = FC<Props> {
    val layoutMode = useContext(LayoutModeContext)
    val (open, setOpen) = useState(true)

    val isAvailable = layoutMode != LayoutMode.PHONE

    Sidebar {
        this.open = open && isAvailable
    }
    SidebarContext.Provider {
        value = SidebarState(open, isAvailable) { setOpen(it) }
        Outlet {}
    }
}

val Sidebar = FC<SidebarProps> { props ->
    val (appState, stale) = useContext(AppStateContext)

    Stack {
        component = aside
        val offset = if (props.open == true) {
            280.px
        } else {
            0.px
        }
        css {
            backgroundColor = Color("#FFFFFF")
            width = offset
            transition = 0.5.s
            zIndex = integer(100)
            gap = 30.px
            padding = if (props.open == true) {
                15.px
            } else {
                0.px
            }
            position = Position.fixed
            top = 0.px
            left = 0.px
            bottom = 0.px
            flexShrink = number(0.0)
            overflowX = Overflow.hidden
        }
        Global {
            this.styles {
                "body" {
                    marginLeft = offset
                }
            }
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

private val SidebarHeader = FC<Props> {
    Link {
        css {
            display = Display.flex
            justifyContent = JustifyContent.center
        }
        to = "/"
        img {
            css {
                height = 40.px
            }
            src = "/static/sidebar_logo.svg"
        }
    }
}

