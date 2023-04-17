package components.redesign.layout

import browser.*
import components.*
import components.layout.*
import components.nouser.*
import components.profile.*
import components.redesign.*
import components.redesign.basic.*
import components.redesign.basic.GlobalErrorMessage
import components.redesign.feedback.FeedbackProvider
import components.redesign.rooms.*
import csstype.*
import emotion.react.*
import react.*
import react.router.*
import tools.confido.state.*

val RootLayout = FC<Props> {
    AppStateWebsocketProvider {
        loadingComponent = LoadingLayout
        RootLayoutInner {}
    }
}

private val RootLayoutInner = FC<Props> {
    val (appState, _) = useContext(AppStateContext)
    var showDemoWelcome by useState(appConfig.demoMode && window.asDynamic().demoDismissed != true)
    Backdrop {
        this.`in` = showDemoWelcome
        css {
            backdropFilter = blur(10.px)
        }
        DemoWelcomeBox { dismissDemo = {showDemoWelcome = false; window.asDynamic().demoDismissed = true}}
    }

    GlobalCss {
        backgroundColor = Color("#F2F2F2")
    }
    GlobalErrorMessage {}
    BackdropProvider {
        FeedbackProvider {
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
                        this.element = CreateRoom.create()
                    }
                }
                /*
                Route {
                    path = "profile"
                    this.element = ReactNode("PROFILE HERE")
                }
                if (appState.isAdmin()) {
                    Route {
                        path = "admin/users"
                        this.element = AdminView.create()
                    }
                }
                 */
            }
        }
    }
}
