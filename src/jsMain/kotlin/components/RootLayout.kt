package components

import components.rooms.Room
import csstype.*
import icons.MenuIcon
import kotlinx.browser.window
import mui.material.*
import mui.system.*
import react.*
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.nav
import react.router.*

external interface RootAppBarProps : Props {
    var hasDrawer: Boolean
    var onDrawerOpen: (() -> Unit)?
}

val RootAppBar = FC<RootAppBarProps> {props ->
    val stale = useContext(AppStateContext).stale

    AppBar {
        position = AppBarPosition.fixed
        sx {
            if (props.hasDrawer)
                paddingLeft = responsive(Breakpoint.sm to 240.px)
        }
        Toolbar {
            if (props.hasDrawer) {
                IconButton {
                    sx {
                        display = responsive(Breakpoint.sm to "none".asDynamic())
                        marginRight = 2.asDynamic()
                    }
                    color = IconButtonColor.inherit
                    onClick = {props.onDrawerOpen?.invoke()}
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

val RootLayout = FC<Props> {
    val clientAppState = useContext(AppStateContext)
    val appState = clientAppState.state

    var drawerOpen by useState(false)

    val window = window.document.body

    // Root element
    mui.system.Box {
        sx {
            display = Display.flex
            height = 100.vh
            alignItems = AlignItems.stretch
        }
        CssBaseline {}

        RootAppBar {
            hasDrawer = true
            onDrawerOpen = {drawerOpen = true}
        }

        mui.system.Box {
            component = nav
            sx {
                width = responsive(Breakpoint.sm to 240.px)
                flexShrink = responsive(Breakpoint.sm to number(0.0))
            }
            Sidebar {
                permanent = true
            }
            Sidebar {
                permanent = false
                isOpen = drawerOpen
                onClose = { drawerOpen = false }
            }
        }
        mui.system.Box {
            component = main
            sx {
                flexGrow = number(1.0)
                overflowX = Overflow.hidden
            }
            Toolbar {}
            Routes {
                Route {
                    index = true
                    path = "/"
                    this.element = Typography.create { +"Welcome to Confido!" }
                }
                Route {
                    path = "room/:roomID/*"
                    this.element = Room.create()
                }
                Route {
                    path = "set_name"

                    this.element = SetNameForm.create()
                }
                Route {
                    path = "edit_questions"

                    this.element = EditQuestions.create()
                }
            }
        }
    }
}
