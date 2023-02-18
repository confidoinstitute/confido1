package components.layout

import components.layout.RootAppBar
import components.nouser.EmailLogin
import components.nouser.LandingPage
import components.profile.VerifyToken
import components.rooms.RoomInviteNoUser
import csstype.Color
import csstype.pct
import csstype.px
import mui.material.Toolbar
import mui.system.*
import react.*
import react.router.Route
import react.router.Routes
import utils.byTheme

val NoUserLayout = FC<Props> {
    /*
    RootAppBar {
        hasDrawer = false
        hasProfileMenu = false
    }
    Toolbar {}
     */
    Box {
        sx {
            margin = 0.px
            padding = 0.px
            width = 100.pct
            height = 100.pct
            background = Color("#6733DA")  // TODO(Prin): Use a palette.
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
                path = "/join/:inviteToken"
                this.element = RoomInviteNoUser.create()
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
        }
    }
}
