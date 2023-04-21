package components.redesign.comments

import Client
import components.*
import components.redesign.*
import components.redesign.basic.*
import components.redesign.forms.*
import csstype.*
import dom.html.*
import emotion.react.*
import hooks.*
import payloads.requests.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import tools.confido.question.*
import tools.confido.spaces.*
import tools.confido.utils.*
import utils.*

external interface AddCommentFieldProps : Props {
    /** The id of the entity containing this comment. Make sure to specify the correct [variant]. */
    var id: String

    /** The type of the entity containing this comment. */
    var variant: CommentInputVariant
    var prediction: Prediction?
}

val AddCommentField = FC<AddCommentDialogProps> { props ->
    if (props.variant == undefined) {
        console.error("Invalid comment variant")
        return@FC
    }

    var attachPrediction by useState(false)
    var commentText by useState("")

    val submit = useCoroutineLock()

    fun createComment(text: String, attachPrediction: Boolean) {
        submit {
            val createdComment = CreateComment(unixNow(), text, attachPrediction)
            val url = when (props.variant) {
                CommentInputVariant.QUESTION -> "${questionUrl(props.id)}/comments/add"
                CommentInputVariant.ROOM -> "${roomUrl(props.id)}/comments/add"
            }

            Client.sendData(url, createdComment, onError = { showError?.invoke(it) }) {
                commentText = ""
            }
        }
    }
    div {
        MultilineTextInput {
            css {
                height = 120.px
                backgroundColor = Color("#FFFFFF")
            }
            placeholder = "What do you have to say?"
            value = commentText
            onChange = { e -> commentText = e.target.value }
        }

        Stack {
            direction = FlexDirection.row
            css {
                flexGrow = number(1.0)
                padding = Padding(5.px, 6.px, 5.px, 15.px)
                justifyContent = JustifyContent.spaceBetween
                fontFamily = sansSerif
                fontSize = 15.px
                lineHeight = 18.px
                gap = 10.px
            }

            props.prediction?.let { pred ->
                if (!attachPrediction) {
                    TextButton {
                        palette = TextPalette.action
                        css {
                            cursor = Cursor.pointer

                            fontFamily = sansSerif
                            fontSize = 15.px
                            lineHeight = 18.px
                            fontWeight = integer(400)
                            padding = 0.px
                            margin = 0.px
                        }
                        +"Attach your current estimate"
                        onClick = { attachPrediction = true }
                    }
                } else {
                    Stack {
                        direction = FlexDirection.row
                        css {
                            alignItems = AlignItems.center
                            gap = 18.px
                        }

                        PredictionAttachment {
                            prediction = pred
                        }
                        IconButton {
                            onClick = { attachPrediction = false }
                            palette = TextPalette.gray
                            CloseIcon {}
                        }
                    }
                }
            } ?: div {} // This is needed to properly justify the "Post" button.

            Button {
                type = ButtonType.submit
                disabled = submit.running
                css {
                    borderRadius = 20.px
                    padding = Padding(5.px, 12.px)
                    margin = 0.px
                    fontWeight = integer(500)
                    fontSize = 15.px
                    lineHeight = 18.px
                }
                onClick = {
                    if (commentText.isNotBlank()) {
                        createComment(commentText, attachPrediction)
                    }
                }

                +"Post"
            }
        }
    }
}