package components.redesign.layout

import components.AppStateContext
import components.AppStateWebsocketProvider
import components.nouser.EmailLoginAlreadyLoggedIn
import components.profile.VerifyToken
import components.redesign.basic.GlobalCss
import components.redesign.rooms.Room
import components.redesign.rooms.RoomInviteLoggedIn
import csstype.Auto
import csstype.Overflow
import csstype.vh
import csstype.vw
import emotion.react.css
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.main
import react.router.Route
import react.router.Routes
import react.useContext

val RootLayout = FC<Props> {
    AppStateWebsocketProvider {
        loadingComponent = LoadingLayout
        RootLayoutInner {}
    }
}

private val RootLayoutInner = FC<Props> {
    GlobalCss {}
    div {
        css {
            width = 100.vw
            height = 100.vh
            overflow = Overflow.hidden
        }
        Routes {
            Route {
                index = true
                path = "/"
                this.element = div.create { +"Welcome to Confido!" }
            }
            Route {
                path = "room/:roomID/*"
                this.element = Room.create()
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
            /*
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
             */
        }
    }
}
