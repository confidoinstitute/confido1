package components.redesign

import components.AppStateContext
import components.redesign.basic.*
import components.redesign.feedback.FeedbackContext
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.rooms.RoomList
import csstype.*
import emotion.react.Global
import emotion.react.css
import emotion.react.styles
import react.*
import react.dom.html.ReactHTML.aside
import react.dom.html.ReactHTML.button
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
    val layoutMode = useContext(LayoutModeContext)

    Stack {
        component = aside
        val transitionTime = 0.5.s
        val offset = if (props.open == true) {
            280.px
        } else {
            0.px
        }
        css {
            backgroundColor = Color("#FFFFFF")
            width = offset
            transition = Transition(ident("width"), transitionTime, 0.s)
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
            boxShadow = BoxShadow(0.px, 0.px, 20.px, rgba(0, 0, 0, 0.2))
        }
        Global {
            this.styles {
                "body" {
                    marginLeft = offset
                    transition = Transition(ident("margin-left"), transitionTime, 0.s)
                }
            }
        }
        SidebarHeader {}
        appState.session.user?.let { user ->
            WorkspaceFrame {
                this.user = user
            }
        }
        Stack {
            css {
                gap = 10.px
            }
            title("Rooms", layoutMode.contentSidePad)
            RoomList {
                small = true
            }
        }
        Stack {
            css {
                gap = 2.px
            }
            FeedbackSidebarAction {}
            /*
            SidebarAction {
                text = "How to use this page"
                disabled = true
            }
            SidebarAction {
                text = "About Confido"
                disabled = true
            }
            */
        }
    }
}

val FeedbackSidebarAction = FC<Props> {
    val feedback = useContext(FeedbackContext)

    SidebarAction {
        text = "Give feedback"
        icon = FeedbackIcon
        onClick = {
            // TODO: Pass page name
            feedback.open(null)
        }
    }
}

external interface SidebarActionProps : Props {
    var text: String
    /** Defaults to false. */
    var disabled: Boolean?
    var onClick: (() -> Unit)?
    var icon: ComponentType<PropsWithClassName>?
}

// TODO: This is very close to DialogMenuItem (with padding changes and text size changes)
private val SidebarAction = FC<SidebarActionProps> { props ->
    // We have an extra Stack rather than using the button directly
    // to avoid the padding from being clickable.
    val disabled = props.disabled ?: false
    Stack {
        css {
            padding = Padding(6.px, 8.px)

            if (!disabled) {
                hover {
                    backgroundColor = Color("#F2F2F2")
                    borderRadius = 12.px
                }
            }

            rippleCss()
        }

        button {
            css {
                all = Globals.unset
                if (!disabled) {
                    cursor = Cursor.pointer
                }
                userSelect = None.none

                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = AlignItems.center
                gap = 16.px

                this.color = color
                if (disabled) {
                    this.opacity = number(0.5)
                }
            }

            div {
                css {
                    flex = None.none
                    display = Display.flex
                    justifyContent = JustifyContent.center
                    alignItems = AlignItems.center
                    height = 30.px
                    width = 30.px
                }
                props.icon?.let {
                    +it.create()
                }
            }

            div {
                css {
                    fontSize = 15.px
                    lineHeight = 18.px
                    fontFamily = sansSerif
                    flexGrow = number(1.0)
                }
                +props.text
            }
            onClick = {
                if (!disabled) {
                    createRipple(it, Color("#999999"))
                    props.onClick?.invoke()
                }
            }
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

