package components.redesign.comments

import components.redesign.CirclesIcon
import components.redesign.CloseIcon
import components.redesign.SymmetricGaussIcon
import components.redesign.basic.DialogCore
import components.redesign.basic.Stack
import components.showError
import csstype.*
import dom.html.HTMLTextAreaElement
import emotion.react.css
import hooks.useCoroutineLock
import payloads.requests.CreateComment
import react.*
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import tools.confido.question.Comment
import tools.confido.question.Prediction
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.NumericSpace
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
    /** The id of the entity containing this comment. Make sure to specify the correct [variant]. */
    var id: String
    /** The type of the entity containing this comment. */
    var variant: CommentInputVariant
    var prediction: Prediction?
}

external interface EditCommentDialogProps : Props {
    var open: Boolean
    var onClose: (() -> Unit)?
    var comment: Comment
}

val AddCommentDialog = FC<AddCommentDialogProps> { props ->
    if (props.variant == undefined) {
        console.error("Invalid comment variant")
        return@FC
    }

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
        if (props.variant == CommentInputVariant.QUESTION) {
            this.attachablePrediction = props.prediction
        }
    }
}

val EditCommentDialog = FC<EditCommentDialogProps> { props ->
    val submit = useCoroutineLock()
    fun editComment(content: String, attachPrediction: Boolean) {
        // TODO: Handle attach prediction change
        submit {
            val url = "${props.comment.urlPrefix}/edit"
            Client.sendData(url, content, onError = { showError?.invoke(it) }) { }
        }
    }

    CommentDialog {
        this.open = props.open
        this.onClose = props.onClose
        this.onSubmit = ::editComment
        this.initialContent = props.comment.content
        this.title = "Editing your comment"
    }
}

private external interface CommentDialogProps : Props {
    var open: Boolean
    var onClose: (() -> Unit)?
    var title: String
    var initialContent: String?
    var onSubmit: ((text: String, attachPrediction: Boolean) -> Unit)?
    var attachablePrediction: Prediction?
}

external interface PredictionAttachmentProps : Props {
    var prediction: Prediction
}

private val PredictionAttachment = FC<PredictionAttachmentProps> { props ->
    val icon = when (props.prediction.dist.space) {
        BinarySpace -> CirclesIcon
        // TODO: We also have an asymmetric gauss icon
        is NumericSpace -> SymmetricGaussIcon
    }

    Stack {
        direction = FlexDirection.row
        css {
            alignItems = AlignItems.center
            gap = 5.px
        }
        +icon.create()
        span {
            css {
                color = Color("#555555")
            }
            +props.prediction.dist.description
        }
    }
}

private val CommentDialog = FC<CommentDialogProps> { props ->
    val initialContent = props.initialContent ?: ""

    var lastInitialContent by useState(initialContent)
    var commentContent by useState(initialContent)
    var attachPrediction by useState(false)

    var textareaRef = useRef<HTMLTextAreaElement>(null)

    if (initialContent != lastInitialContent) {
        lastInitialContent = props.initialContent ?: ""
        commentContent = props.initialContent ?: ""
    }

    useEffect(props.open) {
        // We cannot just use autofocus on the textarea as we want the cursor
        // to be positioned after the text when initial content is set.
        textareaRef.current?.let {
            it.focus()
            it.selectionStart = it.value.length;
        }
    }

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
                        commentContent = initialContent
                        props.onClose?.invoke()
                    }
                }
            }
        }


        Stack {
            textarea {
                ref = textareaRef
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
                value = commentContent
                onChange = { e -> commentContent = e.target.value }
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

                if (props.attachablePrediction != null) {
                    if (!attachPrediction) {
                        button {
                            css {
                                all = Globals.unset
                                cursor = Cursor.pointer
                                userSelect = None.none

                                fontFamily = FontFamily.sansSerif
                                fontSize = 15.px
                                lineHeight = 18.px

                                color = Color("#6319FF") // primary
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
                                prediction = props.attachablePrediction!!
                            }
                            button {
                                css {
                                    all = Globals.unset
                                    userSelect = None.none
                                    cursor = Cursor.pointer

                                    display = Display.flex
                                    alignItems = AlignItems.center
                                }

                                onClick = { attachPrediction = false }
                                CloseIcon {}
                            }
                        }
                    }
                } else {
                    // This is needed to properly justify the "Post" button.
                    div {}
                }

                val buttonDisabled = commentContent.isBlank()
                button {
                    css {
                        all = Globals.unset
                        userSelect = None.none

                        if (buttonDisabled) {
                            backgroundColor = Color("#DDDDDD")
                        } else {
                            backgroundColor = Color("#6319FF") // primary
                            cursor = Cursor.pointer
                        }
                        borderRadius = 20.px
                        padding = Padding(5.px, 12.px)
                        color = Color("#FFFFFF")
                        fontWeight = integer(500)
                    }

                    if (!buttonDisabled) {
                        onClick = {
                            props.onSubmit?.invoke(commentContent, attachPrediction)
                            commentContent = ""
                            props.onClose?.invoke()
                        }
                    }

                    +"Post"
                }
            }
        }
    }
}
