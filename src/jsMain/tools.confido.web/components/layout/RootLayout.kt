package tools.confido.web.components.layout

import tools.confido.api.Api
import tools.confido.web.components.useFollowingPresentation
import tools.confido.web.components.useMyUser
import tools.confido.web.components.AppStateWebsocketProvider
import tools.confido.web.components.*
import tools.confido.web.components.admin.UserAdmin
import tools.confido.web.components.basic.*
import tools.confido.web.components.basic.GlobalErrorMessage
import tools.confido.web.components.calibration.CalibrationPage
import tools.confido.web.components.scores.MyScoresPage
import tools.confido.web.components.feedback.FeedbackProvider
import tools.confido.web.components.presenter.PresenterControlPanel
import tools.confido.web.components.presenter.PresenterControllerProvider
import tools.confido.web.components.profile.UserProfile
import tools.confido.web.components.profile.VerifyToken
import tools.confido.web.components.profile.PasswordResetPage
import tools.confido.web.components.rooms.*
import tools.confido.web.components.search.SearchPage
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
            if (navUrl != window.location.pathname + window.location.search) {
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
    DESKTOP(640.px, 0.px);

    /**
     * Width for pages that can use the whole desktop viewport (tile grids, tables,
     * the dashboard). [contentWidth] remains the narrow reading-column width used
     * by linear content such as comment threads and prediction inputs.
     */
    val wideContentWidth: Length
        get() = when (this) {
            PHONE -> 100.pct
            else -> "min(1140px, calc(100% - 30px))".unsafeCast<Length>()
        }
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
                element = SidebarLayout.create()
                children = buildList {
                    // The dashboard lives inside the sidebar layout so that navigation
                    // is uniform across pages; on phone (no sidebar) it renders its own
                    // header and room list instead.
                    add(jso<RouteObject> {
                        index = true
                        element = Dashboard.create()
                    })
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
                        path = "email_revert"
                        element = VerifyToken.create {
                            api = Api.profileEmailRevert()
                            failureTitle = "Could not restore your e-mail address"
                            successTitle = "Your e-mail address was restored"
                            failureText = "The link is expired or invalid."
                            successText = "Everyone was logged out of your account and its password was removed. Log in with an e-mail link; you can set a new password in User settings."
                        }
                    })
                    add(jso<RouteObject> {
                        path = "password_reset"
                        element = PasswordResetPage.create()
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
                        if (FeatureFlag.ROOM_GROUPS.enabled) add(jso<RouteObject> {
                            path = "new_group"
                            element = CreateRoom.create { isGroup = true }
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
