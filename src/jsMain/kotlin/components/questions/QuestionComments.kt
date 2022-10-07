package components.questions

import components.AppStateContext
import components.DistributionSummary
import csstype.Length
import csstype.Overflow
import csstype.number
import csstype.pct
import icons.CloseIcon
import icons.CommentIcon
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
import mui.system.sx
import payloads.CreatedComment
import react.*
import react.dom.html.ButtonType
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.strong
import react.dom.onChange
import react.router.useLocation
import react.router.useNavigate
import react.router.useParams
import tools.confido.question.Comment
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.utils.unixNow
import utils.*
import kotlin.coroutines.EmptyCoroutineContext

external interface QuestionCommentsProps : Props {
    var question: Question
    var prediction: Prediction?
    var comments: List<Comment>
}

external interface CommentProps : Props {
    var comment: Comment
    var deleteMode: Boolean
}

val Comment = FC<CommentProps> { props ->
    var textAgo by useState("")

    val appState = useContext(AppStateContext)
    val currentUser = appState.state.session.name
    val canDelete = (props.comment.user == currentUser || appState.state.isAdmin) && !appState.stale

    // TODO generalize and fix the "no text on mount" issue
    useEffect(props.comment.timestamp) {
        fun setText() {
            textAgo = durationAgo(unixNow() - props.comment.timestamp)
        }
        setText()
        val interval = setInterval(::setText,5000)

        cleanup {
            clearInterval(interval)
        }
    }

    Card {
        sx {
            marginTop = 2.asDynamic()
            marginBottom = 2.asDynamic()
        }
        CardHeader {
            title = ReactNode(props.comment.user)
            subheader = ReactNode(textAgo)
            avatar = Avatar.create { +"${props.comment.user[0]}" }
            if (props.deleteMode) {
                if (canDelete) {
                    action = IconButton.create {
                        onClick = { console.log("Would delete") }
                        DeleteIcon {}
                    }
                }
            }
        }
        CardContent {
            +props.comment.content
        }
        if (props.comment.prediction != null) {
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
                            distribution = props.comment.prediction?.dist
                    }
                }
            }
        }
    }
}

external interface CommentInputProps : Props {
    var id: String
    var prediction: Prediction?
    var onSubmit: ((CreatedComment) -> Unit)?
}

val CommentInput = FC<CommentInputProps> { props ->
    val stale = useContext(AppStateContext).stale
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
                    val createdComment = CreatedComment(unixNow(), content, attachPrediction)
                    Client.httpClient.postJson("/add_comment/${props.id}", createdComment) {
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
        DialogActions {
            TextField {
                fullWidth = true
                this.placeholder = "Write a comment..."
                this.margin = FormControlMargin.none
                this.name = "content"
                this.value = content
                this.onChange = { content = it.eventValue() }
                this.disabled = stale
                if (errorSend) {
                    this.error = true
                    this.helperText = ReactNode("Comment failed to send. Try again later.")
                }
            }
        }
        DialogActions {
            FormGroup {
                sx {
                    flexGrow = number(1.0)
                }
                FormControlLabel {
                    label = span.create {
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
                        this.disabled = props.prediction == null || stale
                        this.onChange = { _, value -> attachPrediction = value }
                    }
                }
            }
            LoadingButton {
                +"Send"
                disabled = pendingSend || content.isEmpty() || stale
                type = ButtonType.submit
                loading = pendingSend
                loadingPosition = LoadingPosition.end
                endIcon = SendIcon.create()
            }
        }
    }
}

val QuestionComments = FC<QuestionCommentsProps> { props ->
    //var deleteMode by useState(false)
    val count = props.comments.count()
    var open by useState(false)


    IconButton {
        // TODO: Fix
        onClick = { open = true; it.stopPropagation() }

        Badge {
            this.badgeContent = if (count > 0) ReactNode(count.toString()) else null
            this.color = BadgeColor.primary
            CommentIcon {}
        }
    }

    Dialog {
        this.open = open
        this.scroll = DialogScroll.paper
        this.fullWidth = true
        this.maxWidth = "lg"
        this.onClose = { _, _ -> open = false }
        sx {
            ".MuiDialog-paper" {
                minHeight = 50.pct
            }
        }

        if (false)
        AppBar {
            this.position = AppBarPosition.relative
            Toolbar {
                IconButton {
                    CloseIcon {}
                    onClick = { open = false }
                }
                Typography {
                    sx {
                        flexGrow = number(1.0)
                    }
                    +props.question.name
                }
                // TODO deletion and edit mechanism
//                Button {
//                    color = ButtonColor.inherit
//                    onClick = { deleteMode = !deleteMode }
//
//                    + if(deleteMode) "Done" else "Delete"
//                }
            }
        }
        DialogTitle {
            +"Comments"
            Typography {
                +props.question.name
            }
        }
        DialogContent {
            sx {
                flexGrow = number(1.0)
            }
            this.dividers = true
            props.comments.sortedByDescending { it.timestamp }.map {
                Comment {
                    key = it.key()
                    comment = it
                    this.deleteMode = deleteMode
                }
            }
        }
        CommentInput {
            id = props.question.id
            prediction = props.prediction
        }
    }
}