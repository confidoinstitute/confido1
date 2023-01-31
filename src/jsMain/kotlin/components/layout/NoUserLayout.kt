package components.layout

import components.layout.RootAppBar
import components.nouser.EmailLogin
import components.nouser.LandingPage
import components.profile.VerifyToken
import components.rooms.RoomInviteForm
import mui.material.Toolbar
import mui.system.*
import react.*
import react.router.Route
import react.router.Routes
import utils.byTheme

val NoUserLayout = FC<Props> {
    RootAppBar {
        hasDrawer = false
    }
    Toolbar {}
    Box {
        sx {
            margin = byTheme("auto")
            maxWidth = byTheme("lg")
        }
        Routes {
            Route {
                index = true
                path = "/*"
                this.element = LandingPage.create()
            }
            Route {
                index = true
                path = "/email_login"
                this.element = EmailLogin.create()
            }
            Route {
                path = "room/:roomID/invite/:inviteToken"
                this.element = RoomInviteForm.create()
            }
            Route {
                path = "password_reset"
                this.element = VerifyToken.create {
                    url = "/profile/password/reset"
                    failureTitle = "Password reset failed"
                    successTitle = "Password was reset"
                    failureText = "The link is expired or invalid."
                    successText = "Your password has been successfully reset. You can log in by e-mail only now."
                }
            }
        }
    }
}
