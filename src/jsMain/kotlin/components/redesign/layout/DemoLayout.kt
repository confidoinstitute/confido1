package components.redesign.layout

import components.DialogCloseButton
import components.LoginContext
import components.redesign.forms.Button
import components.redesign.nouser.LoginByUserSelectInner
import csstype.*
import emotion.react.css
import kotlinx.js.jso
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.strong
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import react.router.useNavigate
import react.useContext
import utils.AUTO
import utils.roomUrl
import web.location.location

const val REQUEST_WORKSPACE_URL = "https://confido.institute/request-a-workspace.html"
const val DEMO_CONTINUE_URL = "/rooms/demoroom1"


val DemoLoginBox = FC<Props> {
    div {
        css {
            width = 100.pct
            maxWidth = 400.px
            margin = Margin(30.px, 30.px, 60.px)
            padding =  Padding(30.px, 30.px)
        }

        p {
            css {
                marginTop = 0.px
                marginBottom = (30 - 16).px // vpeca's design has 30px, but the MUI autocomplete has 16px padding in itself
                fontSize = 20.px
                lineHeight = 24.px
                textAlign = TextAlign.center
                fontFamily = FontFamily.sansSerif
                fontWeight = integer(700)
            }
            +"Try Confido demo"
        }
        div {
            css {
                marginBottom = 50.px
            }
            LoginByUserSelectInner {
                demoMode = true
            }
        }
    }
}

val DemoWelcomeBox = FC<Props> {
    val navigate = useNavigate()

    div {
        css {
            position = Position.relative
            width = 480.px
            margin = Margin(20.px, 20.px, 60.px)
            padding = 20.px
            borderRadius =  10.px
            backgroundColor = Color("#fff")
            boxShadow = BoxShadow(0.px, 0.px, 40.px, 0.px, rgba(0, 0, 0, 0.2))
            fontSize = 16.px
            lineHeight = 25.px
        }
        //DialogCloseButton { onClose = { navigate(DEMO_CONTINUE_URL) } }
        h1 {
            css {
                marginTop =  0.px
                marginBottom = 24.px
                fontSize = 20.px
                lineHeight = 24.px
                textAlign = TextAlign.center
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
            a {
                css {
                    color = Color("#6B9AFF")
                    textDecorationColor = Color("#6B9AFF")
                }
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
                css {
                    width = 100.pct
                }
                onClick = { navigate(DEMO_CONTINUE_URL) }
                +"Start testing"
            }
        }
    }
}

val DemoLayout = FC<Props> {
    val loginState = useContext(LoginContext)
    div {
        css {
            position = Position.absolute
            top = 0.px
            left = 0.px
            width = 100.vw
            minHeight = 100.vh
            //backgroundImage = url("/static/demo_bg.jpg")
            backgroundColor = Color("#00000080")  // black, 50% transparent
            display = Display.flex
            justifyContent = JustifyContent.center
            alignItems = AlignItems.center
            backgroundPosition = "0 0" as BackgroundPosition
            backgroundSize = BackgroundSize.cover
            backgroundRepeat = BackgroundRepeat.noRepeat
            fontSize = 16.px
            lineHeight = 25.px
            fontFamily = FontFamily.sansSerif
            //fontFamily = "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif" as FontFamily
        }
        if (loginState.isLoggedIn) {
            DemoWelcomeBox {}
        } else {
            DemoLoginBox {}
        }
    }
}
