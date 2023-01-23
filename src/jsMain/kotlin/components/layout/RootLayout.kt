package components.layout

import components.AppStateContext
import components.nouser.EmailLoginAlreadyLoggedIn
import components.profile.AdminView
import components.profile.UserSettings
import components.profile.VerifyToken
import components.rooms.RoomInviteForm
import components.rooms.NewRoom
import components.rooms.Room
import csstype.*
import mui.material.*
import mui.system.*
import react.*
import react.dom.html.ReactHTML.main
import react.router.*
import utils.byTheme
import utils.themed

val RootLayout = FC<Props> {
    val (appState, _) = useContext(AppStateContext)
    var drawerOpen by useState(false)
    console.log("Root layout is being rerendered")

    val theme = mui.material.styles.useTheme<mui.material.styles.Theme>().breakpoints.up(permanentBreakpoint)
    val mediaMatch = useMediaQuery(theme)
    useEffect(mediaMatch) {
        drawerOpen = false
    }


    // Root element
    mui.system.Box {
        key = "rootBox"
        sx {
            display = Display.flex
            height = 100.vh
            alignItems = AlignItems.stretch
        }
        CssBaseline {}

        RootAppBar {
            key = "appbar"
            if (appState.isFullUser)
                hasDrawer = true
            onDrawerOpen = { drawerOpen = true }
        }

        Sidebar {
            key = "sidebar"
            permanent = mediaMatch
            isOpen = drawerOpen
            onClose = { drawerOpen = false }
        }
        mui.system.Box {
            key = "main"
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
                    Route {
                        path = "email_verify"
                        this.element = VerifyToken.create {
                            url = "/profile/email/verify"
                            failureTitle = "Email verification failed"
                            successTitle = "Email verification success"
                            failureText = "The verification link is expired or invalid."
                            successText = "Your email address has been successfully verified."
                        }
                    }
                    Route {
                        path = "password_reset"
                        this.element = VerifyToken.create {
                            url = "/profile/password/reset"
                            failureTitle = "Password undo failed"
                            successTitle = "Password change undone"
                            failureText = "The link is expired or invalid."
                            successText = "Your password has been successfully undone. Please log-in again by e-mail."
                        }
                    }
                    Route {
                        path = "email_login"
                        this.element = EmailLoginAlreadyLoggedIn.create()
                    }
                    if (appState.session.user?.type?.isProper() == true) {
                        Route {
                            path = "new_room"
                            this.element = NewRoom.create()
                        }
                    }
                    Route {
                        path = "profile"
                        this.element = UserSettings.create()
                    }
                    if (appState.isAdmin()) {
                        Route {
                            path = "admin/users"
                            this.element = AdminView.create()
                        }
                    }
                }
            }
        }
    }
}