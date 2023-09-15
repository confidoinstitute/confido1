package components.redesign.layout

import components.LoginContext
import components.cookieSet
import components.nouser.DevModeSection
import components.profile.*
import components.redesign.AboutIcon
import components.redesign.basic.*
import components.redesign.nouser.*
import components.redesign.nouser.EmailLogin
import components.redesign.nouser.LoginForm
import csstype.*
import emotion.react.*
import icons.LockOpenIcon
import react.*
import react.dom.html.AnchorTarget
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.main
import react.router.*
import tools.confido.state.*
import web.timers.clearInterval
import web.timers.setInterval
import kotlin.time.Duration.Companion.seconds

val NoUserLayout = FC<Props> {
    val palette = MainPalette.login
    val loginContext = useContext(LoginContext)

    useEffect {
        val interval = setInterval(5.seconds) {
            if (cookieSet())
                loginContext.login()
        }
        cleanup {
            clearInterval(interval)
        }
    }

    GlobalCss {
        backgroundColor = palette.color
    }
    Stack {
        component = main
        direction = FlexDirection.column
        css {
            alignItems = AlignItems.center
            color = palette.text.color
            minHeight = 100.vh
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
        div {
            css { flexGrow = number(1.0) }
        }
        Stack {
            direction = FlexDirection.row
            css {
                justifyContent = JustifyContent.spaceBetween
                alignSelf = AlignSelf.stretch
                alignItems = AlignItems.center
                "& > *" {
                    color = Color("#DDDDDD80")
                    fontSize = 15.px
                    lineHeight = 18.px
                    fontFamily = sansSerif
                    textDecoration = None.none

                }
            }
            a {
                href = "https://confido.institute/"
                AboutIcon {
                    css {
                        marginRight = (-2).px
                    }
                }
                target = AnchorTarget._blank
            }
            a {
                href = "https://confido.institute/"
                +"About Confido"
                target = AnchorTarget._blank
            }
            div {
                css { flexGrow = number(1.0) }
            }
            if (appConfig.privacyPolicyUrl != null) {
                a {
                    href = appConfig.privacyPolicyUrl
                    target = AnchorTarget._blank
                    LockOpenIcon{
                        css {
                            transform = scale(0.75)
                        }
                    }
                }
                a {
                    href = appConfig.privacyPolicyUrl
                    target = AnchorTarget._blank
                    +"Privacy policy"
                    css { marginRight = 10.px }
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
