package components.redesign.layout

import components.redesign.basic.*
import components.redesign.forms.*
import components.redesign.nouser.*
import csstype.*
import emotion.css.*
import emotion.react.*
import emotion.styled.styled
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ol
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.strong
import react.dom.html.ReactHTML.ul

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
                fontFamily = sansSerif
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

external interface MessageBoxProps : Props {
    var dismiss: () -> Unit
}

val demoWelcomeKeyframes = keyframes {
    from {
        transform = listOf(translate(-50.pct, -50.pct), scale(0)).joinToString(" ") { it.toString() }.asDynamic()
    }

    to {
        transform = listOf(translate(-50.pct, -50.pct), scale(1)).joinToString(" ") { it.toString() }.asDynamic()
    }
}

val MessageBox = div.styled {props, theme ->
    position = Position.absolute
    left = 50.pct
    top = 50.pct
    transform = translate((-50).pct, (-50).pct)
    width = 480.px
    maxWidth = "calc(100vw - 40px)" as Length
    maxHeight = "calc(100vh - 40px)" as Length
    overflow = Auto.auto
    boxSizing = BoxSizing.borderBox
    padding = 20.px
    borderRadius =  10.px
    backgroundColor = Color("#fff")
    boxShadow = BoxShadow(0.px, 0.px, 40.px, 0.px, rgba(0, 0, 0, 0.2))
    fontSize = 15.px
    lineHeight = 18.px
    zIndex = integer(2500)
    animationName = demoWelcomeKeyframes
    animationDuration = 0.25.s
    animationTimingFunction = AnimationTimingFunction.easeOut
}

val DemoWelcomeBox = FC<MessageBoxProps> {props ->
    MessageBox {
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
                onClick = { props.dismiss() }
                +"Start testing"
            }
        }
    }
}

val NewDesignBox = FC<MessageBoxProps> {props ->
    MessageBox {
        css {
            display = Display.flex
            flexDirection = FlexDirection.column
            gap = 20.px
        }
        h1 {
            css {
                margin = 0.px
                fontSize = 20.px
                lineHeight = 24.px
                textAlign = TextAlign.center
            }
            +"Confido's got a new look!"
        }

        div {
            +"We've revamped our app to make it even more user-friendly and visually appealing. Check out what's new:"
        }

        ol {
            css {
                margin = 0.px
                paddingLeft = 1.rem
                display = Display.flex
                flexDirection = FlexDirection.column
                gap = 8.px
            }
            li {+"🎨 Coloured rooms: Admins can now customize rooms with unique colors and icons, making navigation a breeze."}
            li {+"🚥 Question states: Easily navigate between Open, Closed, and Resolved questions with color-coded labels for quick reference."}
            li {+"✨ Redesigned answering: Enjoy the reworked probability visualisation in both yes-no and numeric questions, as well as the input of estimates. Now touch-display friendly!"}
        }

        div {
            +"And that’s just the beginning – there’s much more than can fit in this box!"
        }

        div {
            Button {
                css {
                    width = 100.pct
                    margin = 0.px
                }
                onClick = { props.dismiss() }
                +"Start exploring"
            }
        }
        div {
            css {
                marginTop = 8.px
                fontSize = 13.px
                lineHeight = 16.px
                color = Color("#AAAAAA")
            }
            +"If you need, you can also temporarily switch back any time using the \"Switch to old UI\" option in the \"three dots\" menu on most pages."
        }
    }
}