package tools.confido.web.components.redesign.layout

import tools.confido.api.Api
import tools.confido.web.components.useFollowingPresentation
import tools.confido.web.components.useMyUser
import tools.confido.web.components.AppStateWebsocketProvider
import tools.confido.web.components.redesign.*
import tools.confido.web.components.redesign.admin.UserAdmin
import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.basic.GlobalErrorMessage
import tools.confido.web.components.redesign.calibration.CalibrationPage
import tools.confido.web.components.redesign.scores.MyScoresPage
import tools.confido.web.components.redesign.feedback.FeedbackProvider
import tools.confido.web.components.redesign.presenter.PresenterControlPanel
import tools.confido.web.components.redesign.presenter.PresenterControllerProvider
import tools.confido.web.components.redesign.profile.UserProfile
import tools.confido.web.components.redesign.profile.VerifyToken
import tools.confido.web.components.redesign.rooms.*
import tools.confido.web.components.redesign.search.SearchPage
import web.cssom.*
import emotion.react.css
import tools.confido.web.hooks.useBreakpoints
import tools.confido.web.hooks.useSubscription
import js.objects.unsafeJso as jso
import tools.confido.payloads.responses.WSData
import react.*
import react.router.*
import tools.confido.extensions.ClientExtension
import tools.confido.state.*
import tools.confido.users.UserType
import web.dom.document
import web.window.window
import tools.confido.state.Topics
import tools.confido.refs.Ref

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
/**
 * Navigates the user to the presentation's current target URL.
 * Rendered only when the session is following a presentation.
 */
private external interface PresentationFollowNavigatorProps : Props {
    var roomId: String
}

private val PresentationFollowNavigator = FC<PresentationFollowNavigatorProps> { props ->
    val navigate = useNavigate()
    val ws = useSubscription(Topics.presentationFollow(Ref(props.roomId)))
    val navUrl = (ws as? WSData)?.data
    val lastNavUrl = useRef<String>()

    useEffect(navUrl) {
        if (navUrl != null && navUrl != lastNavUrl.current) {
            lastNavUrl.current = navUrl
            // Only navigate if we're not already at the target URL
            if (navUrl != window.location.pathname) {
                navigate(navUrl)
            }
        }
    }
}

val RootLayout = FC<Props> {
    ThemeProvider {
        theme = { _ -> DefaultTheme }
        AppStateWebsocketProvider {
            loadingComponent = LoadingLayout
            ConnectionBanner {}
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
    val layoutMode = useBreakpoints(LayoutMode.PHONE to 740, LayoutMode.TABLET to 1020, default =LayoutMode.DESKTOP)
    var showDemoWelcome by useState(appConfig.demoMode && window.asDynamic().demoDismissed != true)
    ClientExtension.enabled.forEach {
        it.rootLayoutStartHook()
    }

    // Presentation follow navigation: conditionally render the navigator component
    val followingRoom = useFollowingPresentation()
    if (followingRoom != null) {
        PresentationFollowNavigator {
            roomId = followingRoom.id
        }
    }

    useEffect(layoutMode.ordinal) {
        document.body.className = when (layoutMode) {
            LayoutMode.PHONE -> ClassName("phone tabminus")
            LayoutMode.TABLET -> ClassName("tablet tabplus tabminus")
            LayoutMode.DESKTOP -> ClassName("desktop tabplus")
        }
    }
    
    // Captured before the memo: use()-based accessors inside a useMemo body would
    // not invalidate it on context change.
    val myUser = useMyUser()
    val routes = useMemo(myUser?.type) {
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
                            api = Api.profileEmailVerify()
                            failureTitle = "Email verification failed"
                            successTitle = "Email verification success"
                            failureText = "The verification link is expired or invalid."
                            successText = "Your email address has been successfully verified."
                        }
                    })
                    add(jso<RouteObject> {
                        path = "password_reset"
                        element = VerifyToken.create {
                            api = Api.profilePasswordResetFinish()
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
                    if (myUser?.type?.isProper() == true) {
                        add(jso<RouteObject> {
                            path = "new_room"
                            element = CreateRoom.create()
                        })
                    }
                    add(jso<RouteObject> {
                        path = "search"
                        element = SearchPage.create()
                    })
                    add(jso<RouteObject> {
                        path = "profile"
                        element = UserProfile.create()
                    })
                    add(jso<RouteObject> {
                        path = "calibration"
                        element = CalibrationPage.create()
                    })
                    add(jso<RouteObject> {
                        path = "scores"
                        element = MyScoresPage.create()
                    })
                    if (myUser?.type == UserType.ADMIN) {
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
            PresenterControlPanel {}
        } } } }
    }
}
