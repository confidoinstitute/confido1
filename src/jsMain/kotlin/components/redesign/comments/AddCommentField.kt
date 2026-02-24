package components.redesign.comments

import Client
import components.*
import components.redesign.*
import components.redesign.basic.*
import components.redesign.forms.*
import components.redesign.rooms.RoomContext
import web.cssom.*
import emotion.react.*
import hooks.*
import payloads.requests.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import tools.confido.extensions.ClientExtension
import tools.confido.question.*
import tools.confido.utils.*
import utils.*
import web.html.ButtonType
import web.html.submit

external interface AddCommentFieldProps : Props {
    /** The id of the entity containing this comment. Make sure to specify the correct [variant]. */
    var id: String
    /** The type of the entity containing this comment. */
    var variant: CommentInputVariant
    var prediction: Prediction?
    var template: String?
}

val AddCommentField = FC<AddCommentFieldProps> { props ->
    if (props.variant == undefined) {
        console.error("Invalid comment variant")
        return@FC
    }

    val (appState, _) = use(AppStateContext)!!
    val room = use(RoomContext)

    var attachPrediction by useState(false)
    var commentText by useState(props.template ?: "")

    val submit = useCoroutineLock()

    fun createComment() {
        submit {
            val createdComment = CreateComment(unixNow(), commentText, attachPrediction)
            val url = when (props.variant) {
                CommentInputVariant.QUESTION -> "${questionUrl(props.id)}/comments/add"
                CommentInputVariant.ROOM -> "${roomUrl(props.id)}/comments/add"
            }

            Client.sendData(url, createdComment, onError = { showError(it) }) {
                commentText = ""
                attachPrediction = false
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
                padding = Padding(5.px, 6.px, 5.px, 6.px)
                justifyContent = JustifyContent.spaceBetween
                fontFamily = sansSerif
                fontSize = 15.px
                lineHeight = 18.px
                gap = 10.px
            }

            Stack {
                direction = FlexDirection.row
                css {
                    alignItems = AlignItems.center
                    gap = 10.px
                    flexGrow = number(1.0)
                }
                room?.let { r -> ClientExtension.forEach { it.commentFieldExtra(r, appState, this) } }
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
                }
            }

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
                    val tpl = props.template?.trim() ?: ""
                    if (commentText.isNotBlank() && !(tpl.isNotBlank() && commentText.trim() == tpl)) {
                        createComment()
                    }
                }

                +"Post"
            }
        }
    }
}