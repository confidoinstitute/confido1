package components.layout

import components.AppStateContext
import components.AppStateWebsocketProvider
import components.nouser.EmailLoginAlreadyLoggedIn
import components.profile.AdminView
import components.profile.UserSettings
import components.profile.VerifyToken
import components.rooms.NewRoom
import components.rooms.Room
import components.rooms.RoomInviteLoggedIn
import csstype.*
import kotlinx.js.get
import kotlinx.js.jso
import mui.material.*
import mui.system.*
import react.*
import react.dom.html.ReactHTML.main
import react.router.*
import tools.confido.extensions.ClientExtension
import utils.byTheme
import utils.roomUrl
import utils.themed

val RootLayout = FC<Props> {
    AppStateWebsocketProvider {
        loadingComponent = NoStateLayout
        RootLayoutInner {}
    }
}

val RoomRedirect = FC<Props> {
    val navigate = useNavigate()
    val location = useLocation().pathname.split('/').drop(2).joinToString("/")
    console.log("Redirecting to /rooms/$location")
    useEffect {
        navigate("/rooms/" + location, jso {
            replace = true
        })
    }
}

private val RootLayoutInner = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    var drawerOpen by useState(false)

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
            hasDrawer = true
            onDrawerOpen = { drawerOpen = true }
            isDisconnected = stale
            hasProfileMenu = true
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
                        path = "rooms/:roomID/*"
                        this.element = Room.create()
                    }
                    Route {
                        path = "room/:roomID/*"
                        this.element = RoomRedirect.create()
                    }
                    Route {
                        path = "/join/:inviteToken"
                        this.element = RoomInviteLoggedIn.create()
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
                            url = "/profile/password/reset/finish"
                            failureTitle = "Password reset failed"
                            successTitle = "Password was reset"
                            failureText = "The link is expired or invalid."
                            successText = "Your password has been successfully reset. You can log in by e-mail only now."
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
                    ClientExtension.forEach { it.rootRoutes(this) }
                }
            }
        }
    }
}