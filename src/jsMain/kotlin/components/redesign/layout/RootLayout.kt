package components.redesign.layout

import browser.*
import components.AppStateContext
import components.AppStateWebsocketProvider
import components.layout.RoomRedirect
import components.nouser.EmailLoginAlreadyLoggedIn
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
import csstype.*
import emotion.react.css
import hooks.useBreakpoints
import react.*
import react.router.*
import tools.confido.extensions.ClientExtension
import tools.confido.state.*
import users.UserType

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
    val (appState, _) = useContext(AppStateContext)
    val layoutMode = useBreakpoints(LayoutMode.PHONE to 740, LayoutMode.TABLET to 1020, default =LayoutMode.DESKTOP)
    val location = useLocation()
    var showDemoWelcome by useState(appConfig.demoMode && window.asDynamic().demoDismissed != true)
    var showNewDesign by useState {!appConfig.demoMode && web.storage.localStorage.getItem("newDesignMessageSeen") == null }
    ClientExtension.enabled.forEach {
        it.rootLayoutStartHook()
    }
    useEffect(layoutMode.ordinal) {
        document.body.className = when (layoutMode) {
            LayoutMode.PHONE -> "phone tabminus"
            LayoutMode.TABLET -> "tablet tabplus tabminus"
            LayoutMode.DESKTOP -> "desktop tabplus"
        }
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

        //Backdrop {
        //    this.`in` = showNewDesign
        //    css {
        //        backdropFilter = blur(10.px)
        //    }
        //    NewDesignBox {
        //        dismiss = { showNewDesign = false; web.storage.localStorage.setItem("newDesignMessageSeen", "yes") }
        //    }
        //}

        GlobalCss {
            backgroundColor = UIGrayBg
        }
        GlobalErrorMessage {}
        BackdropProvider {
        SidebarStateProvider {
        FeedbackProvider {
        PresenterControllerProvider {
            Routes {
                Route {
                    index = true
                    path = "/"
                    this.element = Dashboard.create()
                }
                Route {
                    this.element = SidebarLayout.create()
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
                            successText =
                                "Your password has been successfully reset. You can log in by e-mail only now."
                        }
                    }
                    Route {
                        path = "email_login"
                        this.element = EmailLoginAlreadyLoggedIn.create()
                    }
                    if (appState.session.user?.type?.isProper() == true) {
                        Route {
                            path = "new_room"
                            this.element = CreateRoom.create()
                        }
                    }
                    Route {
                        path = "profile"
                        this.element = UserProfile.create()
                    }
                    Route {
                        path = "calibration"
                        this.element = CalibrationPage.create()
                    }
                    if (appState.session.user?.type == UserType.ADMIN) {
                        Route {
                            path = "admin/users"
                            this.element = UserAdmin.create()
                        }
                    }
                }
                /*
        if (appState.isAdmin()) {
            Route {
                path = "admin/users"
                this.element = AdminView.create()
            }
        }
         */
            }
        } } } }
    }
}
