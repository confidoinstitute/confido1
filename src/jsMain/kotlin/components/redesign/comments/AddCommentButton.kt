package components.redesign.comments

import components.redesign.basic.Stack
import csstype.*
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div

external interface AddCommentButtonProps : Props {
    var onClick: (() -> Unit)?
}

val AddCommentButton = FC<AddCommentButtonProps> { props ->
    Stack {
        css {
            backgroundColor = Color("#FFFFFF")
            borderTopLeftRadius = 10.px
            borderTopRightRadius = 10.px
            padding = 15.px
        }
        button {
            css {
                flexGrow = number(1.0)

                all = Globals.unset
                cursor = Cursor.text

                backgroundColor = Color("#F8F8F8")
                border = Border(0.5.px, LineStyle.solid, Color("#CCCCCC"))
                borderRadius = 5.px
                color = Color("#CCCCCC")
            }

            div {
                css {
                    padding = Padding(10.px, 12.px)
                }
                +"Add a comment"
            }

            onClick = { props.onClick?.invoke() }
        }
    }
}
