package components.layout

import components.AppStateContext
import components.DialogCloseButton
import components.nouser.LoginByUserSelectInner
import csstype.*
import emotion.react.css
import kotlinx.js.jso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Link
import mui.material.styles.PaletteColor
import mui.material.styles.createTheme
import react.FC
import react.Props
import react.dom.html.ReactHTML.strong
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import react.useContext
import utils.AUTO
import utils.buildObject
import web.location.location

const val REQUEST_WORKSPACE_URL = "https://confido.institute/request-a-workspace.html"
const val DEMO_CONTINUE_URL = "/room/demoroom1"

val demoTheme = createTheme(
    buildObject {
        this.palette = buildObject {
            this.primary = buildObject<PaletteColor> {
                main = Color("#6733da")
                light = Color("#9e62ff")
                dark = Color("#2700a7")
                contrastText = Color("#ffffff")
            }
        }
        components = jso {
            MuiButton = jso {
                styleOverrides = jso {
                    root = jso {
                        textTransform = "none"
                        padding = Padding(9.px, 15.px)
                        fontSize = 16.px
                        boxShadow = "none"
                    }
                }
            }
            MuiLink = jso {
                styleOverrides = jso {
                    root = jso {
                        color = "#6b9aff"
                        textDecorationColor = "#6b9aff"
                    }
                }
            }
        }
    }
)

val DemoLoginBox = FC<Props> {
    div {
        css {
            width = 370.px
            margin = Margin(30.px, 30.px, 60.px)
            padding =  Padding(30.px, 30.px)
            borderRadius = 12.px
            backgroundColor = Color("white")
            boxShadow = BoxShadow(0.px, 0.px, 40.px, 0.px, rgba(0, 0, 0, 0.2))
        }
        img {
            css {
                display = Display.block
                width = 220.px
                marginRight = AUTO
                marginBottom = 50.px
                marginLeft = AUTO
            }
            src = "/static/logo-horizontal.png"
        }
        h1 {
            css {
                marginTop = 0.px
                marginBottom = (30 - 16).px // vpeca's design has 30px, but the MUI autocomplete has 16px padding in itself
                fontSize = 26.px
                lineHeight = 32.px
                textAlign = TextAlign.center
            }
            +"Try Confido Demo"
        }
        div {
            css {
                marginBottom = 50.px
            }
            LoginByUserSelectInner {
                demoMode = true
            }
            p {
                css {
                    color = Color("#686868")
                    fontSize = 12.px
                    lineHeight = 16.px
                    textAlign =  TextAlign.center
                }
                +"This is a live playground. Do not use for serious purposes – everyone can see the data."
            }
        }

        div {
            css {
                marginBottom = 21.px
                fontSize = 14.px
                lineHeight = 18.px
                fontWeight = 500 as FontWeight
                textAlign = TextAlign.center
            }
            +"Ready to start using Confido?"
            br{}
            Link {
                href=REQUEST_WORKSPACE_URL
                +"Request a workspace for free"
            }
        }
    }
}

val DemoWelcomeBox = FC<Props> {
    div {
        css {
            position = Position.relative
            width = 480.px
            margin = Margin(30.px, 30.px, 60.px)
            padding = 50.px
            borderRadius =  4.px
            backgroundColor = Color("#fff")
            boxShadow = BoxShadow(0.px, 0.px, 40.px, 0.px, rgba(0, 0, 0, 0.2))
            fontSize = 16.px
            lineHeight = 25.px
        }
        DialogCloseButton{ onClose = { location.href = DEMO_CONTINUE_URL } }
        h1 {
            css {
                marginTop =  0.px
                marginBottom = 24.px
                fontSize = 26.px
                lineHeight = 32.px
            }
            +"Welcome to Confido Demo!"
        }
        p { +"This is a live playground for everyone to see how the Confido App looks and works from inside." }
        p {
            strong { +"Do not use it for other purposes than testing." }
            +" Everything you put in is visible to all the other testers! Also, you may lose the data when we reset it to default every midnight."
        }
        p {
            +"If you want to use the Confido App for real, "
            Link {
                href = REQUEST_WORKSPACE_URL
                +"request your own workspace"
                }
            + " – it is free!"
        }
        div {
            css {
                marginTop = 40.px
                textAlign = TextAlign.center
            }
            Button {
                variant = ButtonVariant.contained
                href = DEMO_CONTINUE_URL
                +"Start testing"
            }
        }
    }
}

val DemoLayout = FC<Props> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val loggedIn = appState.session.user != null
    mui.system.ThemeProvider {
        theme = demoTheme
        div {
            css {
                position = Position.absolute
                top = 0.px
                left = 0.px
                width = 100.vw
                minHeight = 100.vh
                backgroundImage = url("/static/demo_bg.jpg")
                display = Display.flex
                justifyContent = JustifyContent.center
            alignItems = AlignItems.center
            backgroundPosition = "0 0" as BackgroundPosition
            backgroundSize = BackgroundSize.cover
            backgroundRepeat = BackgroundRepeat.noRepeat
            fontSize = 16.px
            lineHeight = 25.px
            fontFamily = "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif" as FontFamily
        }
        if (loggedIn) {
            DemoWelcomeBox{}
        } else {
            DemoLoginBox{}
        }
    }
    }

}