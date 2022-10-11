package components

import components.rooms.RoomContext
import csstype.AlignItems
import csstype.number
import icons.DeleteIcon
import icons.SendIcon
import io.ktor.client.plugins.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.js.timers.clearInterval
import kotlinx.js.timers.setInterval
import mui.lab.LoadingButton
import mui.lab.LoadingPosition
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import payloads.requests.CreateComment
import react.*
import react.dom.html.ButtonType
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.strong
import react.dom.onChange
import rooms.RoomPermission
import tools.confido.question.Comment
import tools.confido.question.Prediction
import tools.confido.question.QuestionComment
import tools.confido.refs.eqid
import tools.confido.utils.unixNow
import utils.durationAgo
import utils.eventValue
import utils.postJson
import utils.themed
import kotlin.coroutines.EmptyCoroutineContext

external interface CommentProps : Props {
    var comment: Comment
}

val Comment = FC<CommentProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val currentUser = appState.session.user
    val comment = props.comment
    val user = appState.users[comment.user.id] ?: return@FC

    var textAgo by useState("")
    val canDelete =
        (user eqid currentUser || appState.hasPermission(room, RoomPermission.MANAGE_COMMENTS)) && !stale

    // TODO generalize and fix the "no text on mount" issue
    useEffect(comment.timestamp) {
        fun setText() {
            textAgo = durationAgo(unixNow() - comment.timestamp)
        }
        setText()
        val interval = setInterval(::setText, 5000)

        cleanup {
            clearInterval(interval)
        }
    }

    Card {
        sx {
            marginTop = themed(2)
            marginBottom = themed(2)
        }
        CardHeader {
            // TODO: Handle nickless, email-only names(when appropriate)
            val name = user.nick ?: "Anonymous"
            title = ReactNode(name)
            subheader = ReactNode(textAgo)
            avatar = UserAvatar.create {
                this.user = user
            }
            if (canDelete) {
                action = IconButton.create {
                    // TODO delete API
                    onClick = { console.log("Would delete") }
                    DeleteIcon {}
                }
            }
        }
        CardContent {
            +comment.content
        }
        if (comment is QuestionComment && comment.prediction != null) {
            Divider {}
            CardContent {
                Typography {
                    sx {
                        lineHeight = number(2.0)
                    }
                    strong {
                        +"Prediction: "
                    }
                    DistributionSummary {
                        spoiler = true
                        allowPlotDialog = true
                        distribution = comment.prediction?.dist
                    }
                }
            }
        }
    }
}

enum class CommentInputVariant {
    QUESTION,
    ROOM,
}

external interface CommentInputProps : Props {
    var variant: CommentInputVariant
    var id: String
    var prediction: Prediction?
    var onSubmit: ((CreateComment) -> Unit)?
}

val CommentInput = FC<CommentInputProps> { props ->
    val (_, stale) = useContext(AppStateContext)
    var content by useState("")
    var attachPrediction by useState(false)
    var pendingSend by useState(false)
    var errorSend by useState(false)

    form {
        onSubmit = {
            it.preventDefault()
            CoroutineScope(EmptyCoroutineContext).launch {
                errorSend = false
                pendingSend = true
                try {
                    val createdComment = CreateComment(unixNow(), content, attachPrediction)
                    val url = when(props.variant) {
                        CommentInputVariant.QUESTION -> "/add_comment/${props.id}"
                        CommentInputVariant.ROOM -> "/add_room_comment/${props.id}"
                    }
                    Client.httpClient.postJson(url, createdComment) {
                        expectSuccess = true
                    }
                    content = ""
                    props.onSubmit?.invoke(createdComment)
                } catch (e: Throwable) {
                    errorSend = true
                } finally {
                    pendingSend = false
                }
            }
        }

        fun commentInput(childrenBuilder: ChildrenBuilder) = childrenBuilder.apply {
            TextField {
                fullWidth = true
                this.placeholder = "Write a comment..."
                this.margin = FormControlMargin.none
                this.name = "content"
                this.value = content
                this.onChange = { content = it.eventValue() }
                if (errorSend) {
                    this.error = true
                    this.helperText = ReactNode("Comment failed to send. Try again later.")
                }
            }
        }

        fun sendButton(childrenBuilder: ChildrenBuilder) = childrenBuilder.apply {
            LoadingButton {
                +"Send"
                disabled = pendingSend || content.isEmpty() || stale
                type = ButtonType.submit
                loading = pendingSend
                loadingPosition = LoadingPosition.end
                endIcon = SendIcon.create()
            }
        }

        when(props.variant) {
            CommentInputVariant.QUESTION -> {
                DialogActions {
                    commentInput(this)
                }
                DialogActions {
                    FormGroup {
                        sx {
                            flexGrow = number(1.0)
                        }
                        FormControlLabel {
                            label = ReactHTML.span.create {
                                +"Attach prediction "
                                props.prediction?.let {
                                    Typography {
                                        variant = TypographyVariant.body2
                                        +it.dist.description
                                    }
                                }
                            }
                            control = Checkbox.create {
                                this.name = "attach"
                                this.checked = attachPrediction
                                this.disabled = props.prediction == null
                                this.onChange = { _, value -> attachPrediction = value }
                            }
                        }
                    }
                    sendButton(this)
                }
            }
            CommentInputVariant.ROOM -> {
                Stack {
                    this.direction = responsive(StackDirection.row)
                    this.spacing = themed(2)
                    sx {
                        alignItems = AlignItems.baseline
                    }
                    commentInput(this)
                    sendButton(this)
                }
            }
        }
    }
}