package components.nouser

import components.AppStateContext
import csstype.*
import icons.CloseIcon
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import payloads.requests.PasswordLogin
import react.*
import react.dom.html.ReactHTML.h1
import users.DebugAdmin
import users.DebugMember
import utils.byTheme
import utils.themed

val LandingPage = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)

    Typography { +"Welcome to Confido!" }

    if (appState.demoMode) {
        Typography {
            align = TypographyAlign.center
            variant = TypographyVariant.h5
            component = h1
            +"Log in to Confido demo"
        }

        LoginByUserSelectForm {
            helperText = "Try any account to see Confido from their point of view."
        }
    } else {
        LoginForm {}
    }

    if (appState.devMode) {
        DevModeSection {}
    }
    // TODO: Landing page.
}

val DevModeSection = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    var hidden by useState(false)

    if (hidden) return@FC

    Container {
        sx {
            marginTop = themed(2)
        }
        maxWidth = byTheme("xs")
        Card {
            CardHeader {
                title = ReactNode("Developer mode")
                action = Tooltip.create {
                    title = ReactNode("Hide this dev section (for screenshots and such); refresh to restore.")
                    IconButton {
                        CloseIcon {
                            sx {
                                width = 18.px
                                height = 18.px
                            }
                        }
                        onClick = {
                            hidden = true
                        }
                    }
                }
            }

            CardContent {
                LoginByUserSelectForm {}
            }

            CardActions {
                Button {
                    onClick = {
                        Client.postData("/login", PasswordLogin(DebugAdmin.email, DebugAdmin.password))
                    }
                    disabled = stale
                    +"Log in as debug admin"
                }
                Button {
                    onClick = {
                        Client.postData("/login", PasswordLogin(DebugMember.email, DebugMember.password))
                    }
                    disabled = stale
                    +"Log in as debug member"
                }

            }
        }
    }
}
