package components.redesign.layout

import components.DialogCloseButton
import components.LoginContext
import components.redesign.forms.Button
import components.redesign.nouser.LoginByUserSelectInner
import csstype.*
import emotion.css.keyframes
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

external interface DemoWelcomeBoxProps : Props {
    var dismissDemo: () -> Unit
}

val demoWelcomeKeyframes = keyframes {
    from {
        transform = listOf(translate(-50.pct, -50.pct), scale(0)).joinToString(" ") { it.toString() }.asDynamic()
    }

    to {
        transform = listOf(translate(-50.pct, -50.pct), scale(1)).joinToString(" ") { it.toString() }.asDynamic()
    }
}

val DemoWelcomeBox = FC<DemoWelcomeBoxProps> {props ->
    div {
        css {
            position = Position.absolute
            left = 50.pct
            top = 50.pct
            transform = translate(-50.pct, -50.pct)
            width = 480.px
            padding = 20.px
            borderRadius =  10.px
            backgroundColor = Color("#fff")
            boxShadow = BoxShadow(0.px, 0.px, 40.px, 0.px, rgba(0, 0, 0, 0.2))
            fontSize = 16.px
            lineHeight = 25.px
            zIndex = integer(2500)
            animationName = demoWelcomeKeyframes
            animationDuration = 0.25.s
            animationTimingFunction = AnimationTimingFunction.easeOut
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
                onClick = { props.dismissDemo() }
                +"Start testing"
            }
        }
    }
}
