package components.redesign.layout

import components.nouser.DevModeSection
import components.profile.*
import components.redesign.basic.*
import components.redesign.nouser.*
import components.redesign.nouser.EmailLogin
import components.redesign.nouser.LoginForm
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.main
import react.router.*
import tools.confido.state.*

val NoUserLayout = FC<Props> {
    val palette = MainPalette.login
    GlobalCss {
        backgroundColor = palette.color
    }
    Stack {
        component = main
        direction = FlexDirection.column
        css {
            alignItems = AlignItems.center
            color = palette.text.color
        }
        LogoWithText {}

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

val LandingPage = FC<Props> {
    if (appConfig.demoMode) {
        DemoLoginBox {}
    } else {
        LoginForm {}
    }

    if (appConfig.devMode) {
        DevModeSection {}
    }
}
