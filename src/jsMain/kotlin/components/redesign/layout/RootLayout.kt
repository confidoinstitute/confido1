package components.redesign.layout

import components.AppStateContext
import components.AppStateWebsocketProvider
import components.redesign.*
import components.redesign.admin.UserAdmin
import components.redesign.basic.*
import components.redesign.basic.GlobalErrorMessage
import components.redesign.calibration.CalibrationPage
import components.redesign.feedback.FeedbackProvider
import components.redesign.presenter.PresenterControllerProvider
import components.redesign.profile.UserProfile
import components.redesign.profile.VerifyToken
import components.redesign.rooms.*
import web.cssom.*
import emotion.react.css
import hooks.useBreakpoints
import js.objects.unsafeJso as jso
import react.*
import react.router.*
import tools.confido.extensions.ClientExtension
import tools.confido.state.*
import users.UserType
import web.dom.document
import web.window.window

val RoomRedirect = FC<Props> {
    val navigate = useNavigate()
    val location = useLocation().pathname.split('/').drop(2).joinToString("/")
    console.log("Redirecting to /rooms/$location")
    useEffect {
        navigate("/rooms/" + location, jso<dynamic> {
            replace = true
        })
    }
}


val EmailLoginAlreadyLoggedIn = FC<Props> {
    val navigate = useNavigate()

    useEffect {
        navigate("/")
    }
}
val RootLayout = FC<Props> {
    ThemeProvider {
        theme = { _ -> DefaultTheme }
        AppStateWebsocketProvider {
            loadingComponent = LoadingLayout
            RootLayoutInner {}
        }
    }
}

enum class LayoutMode(val contentWidth: Length, val contentSidePad: Length) {
    PHONE(100.pct, 20.px),
    TABLET(640.px, 0.px),
    DESKTOP(640.px, 0.px),
}

val LayoutModeContext = createContext<LayoutMode>()

private val RootLayoutInner = FC<Props> {
    val (appState, _) = use(AppStateContext)!!
    val layoutMode = useBreakpoints(LayoutMode.PHONE to 740, LayoutMode.TABLET to 1020, default =LayoutMode.DESKTOP)
    var showDemoWelcome by useState(appConfig.demoMode && window.asDynamic().demoDismissed != true)
    ClientExtension.enabled.forEach {
        it.rootLayoutStartHook()
    }
    useEffect(layoutMode.ordinal) {
        document.body.className = when (layoutMode) {
            LayoutMode.PHONE -> ClassName("phone tabminus")
            LayoutMode.TABLET -> ClassName("tablet tabplus tabminus")
            LayoutMode.DESKTOP -> ClassName("desktop tabplus")
        }
    }
    
    val routes = useMemo(appState.session.user?.type) {
        buildList {
            add(jso<RouteObject> {
                index = true
                path = "/"
                element = Dashboard.create()
            })
            add(jso<RouteObject> {
                element = SidebarLayout.create()
                children = buildList {
                    add(jso<RouteObject> {
                        path = "rooms/:roomID/*"
                        element = Room.create()
                    })
                    add(jso<RouteObject> {
                        path = "room/:roomID/*"
                        element = RoomRedirect.create()
                    })
                    add(jso<RouteObject> {
                        path = "/join/:inviteToken"
                        element = RoomInviteLoggedIn.create()
                    })
                    add(jso<RouteObject> {
                        path = "email_verify"
                        element = VerifyToken.create {
                            url = "/profile/email/verify"
                            failureTitle = "Email verification failed"
                            successTitle = "Email verification success"
                            failureText = "The verification link is expired or invalid."
                            successText = "Your email address has been successfully verified."
                        }
                    })
                    add(jso<RouteObject> {
                        path = "password_reset"
                        element = VerifyToken.create {
                            url = "/profile/password/reset/finish"
                            failureTitle = "Password reset failed"
                            successTitle = "Password was reset"
                            failureText = "The link is expired or invalid."
                            successText = "Your password has been successfully reset. You can log in by e-mail only now."
                        }
                    })
                    add(jso<RouteObject> {
                        path = "email_login"
                        element = EmailLoginAlreadyLoggedIn.create()
                    })
                    if (appState.session.user?.type?.isProper() == true) {
                        add(jso<RouteObject> {
                            path = "new_room"
                            element = CreateRoom.create()
                        })
                    }
                    add(jso<RouteObject> {
                        path = "profile"
                        element = UserProfile.create()
                    })
                    add(jso<RouteObject> {
                        path = "calibration"
                        element = CalibrationPage.create()
                    })
                    if (appState.session.user?.type == UserType.ADMIN) {
                        add(jso<RouteObject> {
                            path = "admin/users"
                            element = UserAdmin.create()
                        })
                    }
                    // Extension routes
                    ClientExtension.forEach { ext ->
                        addAll(ext.rootRoutesExtra())
                    }
                }.toTypedArray()
            })
        }.toTypedArray()
    }
    
    LayoutModeContext.Provider {
        value = layoutMode
        Backdrop {
            this.`in` = showDemoWelcome
            css {
                backdropFilter = blur(10.px)
            }
            onClick = {
                if (it.target == it.currentTarget) {
                    showDemoWelcome = false
                    window.asDynamic().demoDismissed = true
                }
            }
            DemoWelcomeBox { dismiss = { showDemoWelcome = false; window.asDynamic().demoDismissed = true } }
        }

        GlobalCss {
            backgroundColor = UIGrayBg
        }
        GlobalErrorMessage {}
        BackdropProvider {
        SidebarStateProvider {
        FeedbackProvider {
        PresenterControllerProvider {
            +useRoutes(routes)
        } } } }
    }
}
