package components.nouser

import csstype.*
import icons.CloseIcon
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.h1
import tools.confido.state.appConfig
import utils.byTheme
import utils.themed

val LandingPage = FC<Props> {
    if (appConfig.demoMode) {
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

    if (appConfig.devMode) {
        DevModeSection {}
    }
    // TODO: Landing page.
}

val DevModeSection = FC<Props> {
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
        }
    }
}
