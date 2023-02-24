package components.redesign.layout

import browser.window
import components.AppStateContext
import components.AppStateWebsocketProvider
import components.layout.RoomRedirect
import components.nouser.EmailLoginAlreadyLoggedIn
import components.profile.VerifyToken
import components.redesign.Dashboard
import components.redesign.basic.Backdrop
import components.redesign.basic.GlobalCss
import components.redesign.rooms.Room
import components.redesign.rooms.RoomInviteLoggedIn
import csstype.*
import emotion.react.css
import react.*
import react.router.Navigate
import react.router.Route
import react.router.Routes
import tools.confido.state.appConfig

val RootLayout = FC<Props> {
    AppStateWebsocketProvider {
        loadingComponent = LoadingLayout
        RootLayoutInner {}
    }
}

private val RootLayoutInner = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)

    var showDemoWelcome by useState(appConfig.demoMode && window.asDynamic().demoDismissed != true)

    if (showDemoWelcome) {
        Backdrop {
            css {
                backdropFilter = blur(10.px)
                background = None.none
            }
        }
        DemoWelcomeBox { dismissDemo = {showDemoWelcome = false; window.asDynamic().demoDismissed = true}}
    }

    GlobalCss {
        backgroundColor = Color("#F2F2F2")
    }
    Routes {
        Route {
            index = true
            path = "/"
            this.element = Dashboard.create()
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
                this.element = ReactNode("NEW ROOM HERE")
            }
        }
        Route {
            path = "profile"
            this.element = ReactNode("PROFILE HERE")
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
}
