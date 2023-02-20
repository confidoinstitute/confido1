package components.redesign.layout

import components.nouser.DevModeSection
import components.nouser.LoginByUserSelectForm
import components.redesign.basic.GlobalCss
import components.redesign.basic.MainPalette
import components.redesign.basic.Stack
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
    GlobalCss {}
    Stack {
        component = main
        direction = FlexDirection.column
        css {
            width = 100.vw
            height = 100.vh
            overflow = Auto.auto
            alignItems = AlignItems.center
            color = palette.text.color
            backgroundColor = palette.color
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
                this.element = Fragment.create()
            }
            Route {
                path = "/join/:inviteToken"
                this.element = RoomInviteNoUser.create()
            }
            Route {
                path = "password_reset"
                this.element = Fragment.create {
                    //url = "/profile/password/reset/finish"
                    //failureTitle = "Password reset failed"
                    //successTitle = "Password was reset"
                    //failureText = "The link is expired or invalid."
                    //successText = "Your password has been successfully reset. You can log in by e-mail only now."
                }
            }
        }
    }
}

val LandingPage = FC<Props> {
    if (appConfig.demoMode) {
        Typography {
            align = TypographyAlign.center
            variant = TypographyVariant.h5
            component = ReactHTML.h1
            +"Log in to Confido demo"
        }

        LoginByUserSelectForm {
            helperText = "Try any account to see Confido from their point of view."
        }
    } else {
        LoginForm {}
    }

    if (appConfig.devMode) {
        DevModeSection {}
    }
}
