package components.redesign

import browser.window
import components.AppStateContext
import components.redesign.basic.*
import components.redesign.feedback.FeedbackContext
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.rooms.RoomLink
import components.redesign.rooms.RoomList
import csstype.*
import emotion.react.Global
import emotion.react.css
import emotion.react.styles
import icons.LockOpenIcon
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.aside
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.svg.ReactSVG
import react.router.Outlet
import react.router.dom.Link
import react.router.useLocation
import tools.confido.state.appConfig
import web.location.location

external interface SidebarProps : PropsWithChildren {
    var open: Boolean?
    var width: Length
}

class SidebarState internal constructor(
    val isOpen: Boolean,
    val isAvailableInLayout: Boolean,
    val isAvailableInPage: Boolean,
    var layoutMode: LayoutMode,
    val setOpenState: (Boolean) -> Unit,
    val setAvailableState: (Boolean) -> Unit,
) {
    fun open() {
        setOpenState(true)
    }

    fun close() {
        setOpenState(false)
    }

    val isAvailable get() = isAvailableInPage && isAvailableInLayout

    val marginOffset
        get(): Length = if (isOpen && isAvailable) {
            280.px
        } else {
            0.px
        }

    fun toggle() {
        setOpenState(!isOpen)
    }
    
    fun closeIfTablet() {
        if (layoutMode == LayoutMode.TABLET) close()
    }
}

val SidebarContext = createContext<SidebarState>()

val SidebarStateProvider = FC<PropsWithChildren> { props ->
    val layoutMode = useContext(LayoutModeContext)
    val (open, setOpen) = useState(true)
    val (availableInPage, setAvailableInPage) = useState(false)
    val location = useLocation()

    useBackdrop(open && layoutMode == LayoutMode.TABLET) { setOpen(false) }

    useEffect(location) {
        // close overlay sidebar after navigation
        if (layoutMode == LayoutMode.TABLET) setOpen(false)
    }

    val isAvailable = layoutMode != LayoutMode.PHONE

    val state = SidebarState(open, isAvailable, availableInPage, layoutMode, { setOpen(it) }, { setAvailableInPage(it) })

    useEffect(layoutMode.ordinal) {
        state.layoutMode = layoutMode
        setOpen(when (layoutMode) {
            LayoutMode.PHONE -> false
            LayoutMode.TABLET -> false
            LayoutMode.DESKTOP -> true
        })
    }
    SidebarContext.Provider {
        value = state
        +props.children
    }
}

val SidebarLayout = FC<Props> {
    val sidebarState = useContext(SidebarContext)

    useEffect {
        sidebarState.setAvailableState(true)
        cleanup {
            sidebarState.setAvailableState(false)
        }
    }

    Sidebar {
        this.open = sidebarState.isOpen && sidebarState.isAvailable
        this.width = sidebarState.marginOffset
    }
    Outlet {}
}

external interface SidebarActionsProps: Props {
    var small: Boolean
    var hideCalib: Boolean
}
val SidebarActions = FC<SidebarActionsProps> {props->
    val iconSize = if(props.small) 36.px else 48.px
    val iconSizeInner = if(props.small) 34 else 46
    val (appState, _) = useContext(AppStateContext)

    if (!props.hideCalib)
    RoomLink {
        key = "::calibration"
        to = "/calibration"
        small = props.small
        highlighted = location.pathname == "/calibration"
        div {
            css {
                //border = Border(1.px, LineStyle.solid, Color("#BBBBBB"))
                //borderRadius = 8.px
                width = iconSize
                height = iconSize
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                flexShrink = number(0.0)
            }
            CompassIcon {
                size = iconSizeInner
                color = "#bbb"
            }
        }
        ReactHTML.span {
            +"Calibration"
        }
    }

    if (appState.isAdmin())
    RoomLink {
        key = "::users"
        to = "/admin/users"
        small = props.small
        highlighted = location.pathname == "/admin/users"
        div {
            css {
                //border = Border(1.px, LineStyle.solid, Color("#BBBBBB"))
                //borderRadius = 8.px
                width = iconSize
                height = iconSize
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                flexShrink = number(0.0)
            }
            UsersIcon {
                css { width = 80.pct; height = 80.pct; }
                color = "#bbb"
            }
        }
        ReactHTML.span {
            +"Users"
        }
    }
}

val Sidebar = FC<SidebarProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val layoutMode = useContext(LayoutModeContext)

    Stack {
        component = aside
        val transitionTime = 0.5.s
        val offset = props.width
        css {
            backgroundColor = Color("#FFFFFF")
            width = offset
            //transition = Transition(ident("width"), transitionTime, 0.s)
            zIndex = integer(if (layoutMode == LayoutMode.TABLET) 2050 else 100)
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
                    if (layoutMode == LayoutMode.DESKTOP)
                    marginLeft = offset
                    //transition = Transition(ident("margin-left"), transitionTime, 0.s)
                }
            }
        }
        SidebarHeader {}
        appState.session.user?.let { user ->
            AccountFrame {
                this.user = user
            }
        }
        Stack {
            css {
                gap = 10.px
            }
            title("Workspace", layoutMode.contentSidePad)
            Stack {
                css {
                    gap = 5.px
                }
                SidebarActions { small = true }
            }
            title("Rooms", layoutMode.contentSidePad)
            RoomList {
                canCreate = appState.session.user?.type?.isProper() ?: false
                small = true
            }
        }
        Stack {
            css {
                gap = 2.px
            }
            FeedbackSidebarAction {}
            /*
            When implementing these, see also the DialogMenuCommonActions, this is duplicated there.

            SidebarAction {
                text = "How to use this page"
                disabled = true
            }
            */
            SidebarAction {
                text = "About Confido"
                icon = AboutIcon
                onClick = {
                    // Warning: This is currently duplicated in DialogMenuCommonActions
                    window.open("https://confido.institute/confido-app.html", "_blank")
                }
            }
            if (appConfig.privacyPolicyUrl != null)
            SidebarAction {
                icon = (LockOpenIcon as FC<PropsWithClassName>).withStyle {
                    transform = scale(0.75)
                    position = Position.relative
                    top = (-2).px
                }
                text = "Privacy policy"
                onClick = {
                    // Warning: This is currently duplicated in DialogMenuCommonActions
                    window.open(appConfig.privacyPolicyUrl, "_blank")
                }
            }
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
    var icon: ComponentType<IconProps>?
}

// TODO: This is very close to DialogMenuItem (with padding changes and text size changes)
private val SidebarAction = FC<SidebarActionProps> { props ->
    // We have an extra Stack rather than using the button directly
    // to avoid the padding from being clickable.
    val disabled = props.disabled ?: false
    val sidebarState = useContext(SidebarContext)
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
                    sidebarState.closeIfTablet()
                    props.onClick?.invoke()
                }
            }
        }
    }
}

private val SidebarHeader = FC<Props> {
    Stack {
        direction = FlexDirection.row
        css {
            gap = 5.px
            justifyContent = JustifyContent.center
        }
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
        if (appConfig.demoMode) {
            DemoPill {
                small = true
            }
        }
    }
}

