package components.redesign.layout

import components.LoginContext
import components.cookieSet
import components.redesign.AboutIcon
import components.redesign.basic.*
import components.redesign.nouser.*
import components.redesign.nouser.EmailLogin
import components.redesign.nouser.LoginForm
import components.redesign.profile.VerifyToken
import web.cssom.*
import emotion.react.*
import js.objects.unsafeJso as jso
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.main
import react.router.*
import tools.confido.state.*
import web.timers.clearInterval
import web.timers.setInterval
import web.window.WindowTarget
import web.window._blank
import kotlin.time.Duration.Companion.seconds

val NoUserLayout = FC<Props> {
    val palette = MainPalette.login
    val loginContext = use(LoginContext)!!

    useEffectWithCleanup {
        val interval = setInterval(5.seconds) {
            if (cookieSet())
                loginContext.login()
        }
        onCleanup {
            clearInterval(interval)
        }
    }

    val routes = useMemo {
        arrayOf(
            jso<RouteObject> {
                index = true
                path = "/*"
                element = LandingPage.create()
            },
            jso<RouteObject> {
                path = "/email_login"
                element = EmailLogin.create()
            },
            jso<RouteObject> {
                path = "/join/:inviteToken"
                element = RoomInviteNoUser.create()
            },
            jso<RouteObject> {
                path = "password_reset"
                element = VerifyToken.create {
                    url = "/profile/password/reset/finish"
                    failureTitle = "Password reset failed"
                    successTitle = "Password was reset"
                    failureText = "The link is expired or invalid."
                    successText = "Your password has been successfully reset. You can log in by e-mail only now."
                }
            }
        )
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

        +useRoutes(routes)

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
                target = WindowTarget._blank
            }
            a {
                href = "https://confido.institute/confido-app.html"
                +"About Confido"
                target = WindowTarget._blank
            }
            div {
                css { flexGrow = number(1.0) }
            }
            if (appConfig.privacyPolicyUrl != null) {
                // a {
                //     href = appConfig.privacyPolicyUrl
                //     target = WindowTarget._blank
                //     LockOpenIcon{
                //         css {
                //             transform = scale(0.75)
                //         }
                //     }
                // }
                a {
                    href = appConfig.privacyPolicyUrl
                    target = WindowTarget._blank
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
        LoginByUserSelectForm {}
    }
}
