package components.redesign.comments

import components.redesign.basic.*
import components.redesign.forms.*
import csstype.*
import emotion.react.*
import react.*

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
            boxShadow = BoxShadow(0.px, 0.px, 6.px, rgba(0, 0, 0, 0.2))
        }
        ButtonBase {
            css {
                flexGrow = number(1.0)

                backgroundColor = Color("#F8F8F8")
                border = Border(0.5.px, LineStyle.solid, Color("#CCCCCC"))
                borderRadius = 5.px
                color = Color("#CCCCCC")
                padding = Padding(10.px, 12.px)

                fontFamily = sansSerif
                fontSize = 17.px
                lineHeight = 21.px
                fontWeight = integer(400)
                textAlign = TextAlign.left

                ".ripple" {
                    backgroundColor = Color("#CCCCCC")
                }
            }
            +"Add a comment"
            onClick = { props.onClick?.invoke() }
        }
    }
}
