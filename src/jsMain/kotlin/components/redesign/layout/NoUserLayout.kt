package components.redesign.layout

import components.nouser.DevModeSection
import components.nouser.LoginByUserSelectForm
import components.profile.VerifyToken
import components.redesign.basic.GlobalCss
import components.redesign.basic.MainPalette
import components.redesign.basic.Stack
import components.redesign.nouser.EmailLogin
import components.redesign.nouser.LoginForm
import components.redesign.nouser.RoomInviteNoUser
import csstype.*
import emotion.react.css
import mui.material.Typography
import mui.material.TypographyAlign
import mui.material.styles.TypographyVariant
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.main
import react.router.Route
import react.router.Routes
import tools.confido.state.appConfig

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
