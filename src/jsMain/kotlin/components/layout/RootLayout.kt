package components.layout

import components.AppStateContext
import components.profile.SetNickForm
import components.rooms.RoomInviteForm
import components.rooms.NewRoom
import components.rooms.Room
import csstype.*
import mui.material.*
import mui.system.*
import react.*
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.nav
import react.router.*
import utils.byTheme
import utils.themed

val permanentBreakpoint = Breakpoint.md

val RootLayout = FC<Props> {
    val (appState, _) = useContext(AppStateContext)
    var drawerOpen by useState(false)

    val theme = mui.material.styles.useTheme<mui.material.styles.Theme>().breakpoints.up(permanentBreakpoint)
    val mediaMatch = useMediaQuery(theme)
    useEffect(mediaMatch) {
        drawerOpen = false
    }


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
                width = responsive(permanentBreakpoint to sidebarWidth)
                flexShrink = responsive(permanentBreakpoint to number(0.0))
            }
            Sidebar {
                permanent = mediaMatch
                isOpen = drawerOpen
                onClose = { drawerOpen = false }
            }
        }
        mui.system.Box {
            component = main
            sx {
                flexGrow = number(1.0)
                overflowX = Overflow.hidden
                padding = themed(1)
            }
            Toolbar {}
            mui.system.Box {
                sx {
                    margin = byTheme("auto")
                    maxWidth = byTheme("lg")
                }
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
                        path = "room/:roomID/invite/:inviteToken"
                        this.element = RoomInviteForm.create()
                    }
                    if (appState.session.user?.type?.isProper() == true) {
                        Route {
                            path = "new_room"
                            this.element = NewRoom.create()
                        }
                        Route {
                            path = "set_name"
                            this.element = SetNickForm.create()
                        }
                    }
                }
            }
        }
    }
}
