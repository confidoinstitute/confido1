package components.redesign.comments

import components.redesign.basic.DialogCore
import components.redesign.basic.Stack
import components.showError
import csstype.*
import emotion.react.css
import hooks.useCoroutineLock
import payloads.requests.CreateComment
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.textarea
import react.useState
import tools.confido.utils.unixNow
import utils.questionUrl
import utils.roomUrl

enum class CommentInputVariant {
    QUESTION,
    ROOM,
}

external interface AddCommentDialogProps : Props {
    var open: Boolean
    var onClose: (() -> Unit)?
    var id: String
    var variant: CommentInputVariant
}

val AddCommentDialog = FC<AddCommentDialogProps> { props ->
    val submit = useCoroutineLock()
    fun createComment(text: String, attachPrediction: Boolean) {
        submit {
            val createdComment = CreateComment(unixNow(), text, attachPrediction)
            val url = when (props.variant) {
                CommentInputVariant.QUESTION -> "${questionUrl(props.id)}/comments/add"
                CommentInputVariant.ROOM -> "${roomUrl(props.id)}/comments/add"
            }

            Client.sendData(url, createdComment, onError = { showError?.invoke(it) }) { }
        }
    }

    CommentDialog {
        this.open = props.open
        this.onClose = props.onClose
        this.onSubmit = ::createComment
        this.title = "Writing a new comment"
    }
}

private external interface CommentDialogProps : Props {
    var open: Boolean
    var onClose: (() -> Unit)?
    var title: String
    var onSubmit: ((text: String, attachPrediction: Boolean) -> Unit)?
}

private val CommentDialog = FC<CommentDialogProps> { props ->
    var commentText by useState("")
    var attachPrediction by useState(false)

    DialogCore {
        open = props.open
        onClose = { props.onClose?.invoke() }
        header = Stack.create {
            direction = FlexDirection.row
            css {
                flexGrow = number(1.0)

                padding = Padding(9.px, 15.px)
                justifyContent = JustifyContent.spaceBetween
                fontFamily = FontFamily.sansSerif
                fontSize = 12.px
                lineHeight = 14.px
                fontWeight = integer(600)
            }

            div {
                css {
                    color = Color("#BBBBBB")
                }
                +props.title
            }
            div {
                css {
                    color = Color("#FF0000")
                }
                button {
                    css {
                        all = Globals.unset
                        cursor = Cursor.pointer
                    }
                    +"Discard"
                    onClick = {
                        commentText = ""
                        props.onClose?.invoke()
                    }
                }
            }
        }


        Stack {
            textarea {
                css {
                    border = None.none
                    outline = None.none
                    resize = None.none
                    backgroundColor = Color("#FFFFFF")

                    padding = Padding(4.px, 15.px)
                    fontFamily = FontFamily.sansSerif
                    fontSize = 15.px
                    lineHeight = 18.px
                    color = Color("#000000")
                    // TODO: Better height scaling, this is just using the first thing that came to mind.
                    height = 40.vh
                }
                value = commentText
                onChange = { e -> commentText = e.target.value }
            }

            Stack {
                direction = FlexDirection.row
                css {
                    flexGrow = number(1.0)
                    borderTop = Border(0.5.px, LineStyle.solid, Color("#DDDDDD"))
                    padding = Padding(5.px, 6.px, 5.px, 15.px)
                    justifyContent = JustifyContent.spaceBetween
                    fontFamily = FontFamily.sansSerif
                    fontSize = 15.px
                    lineHeight = 18.px
                }

                div {
                    // This just helps justify the "Post" button to the right.
                    // The "attach estimate" button below will replace it when it's implemented.
                }
                /*
                // TODO: Implement estimate attachment (well, rendering it, at least)
                // TODO: remove the div above
                button {
                    +"Attach your current estimate"
                }
                 */

                button {
                    css {
                        all = Globals.unset
                        cursor = Cursor.pointer
                        backgroundColor = Color("#6319FF")
                        borderRadius = 20.px
                        padding = Padding(5.px, 12.px)
                        color = Color("#FFFFFF")
                        fontWeight = integer(500)
                    }

                    onClick = {
                        props.onSubmit?.invoke(commentText, attachPrediction)
                        commentText = ""
                        props.onClose?.invoke()
                    }

                    +"Post"
                }
            }
        }
    }
}
