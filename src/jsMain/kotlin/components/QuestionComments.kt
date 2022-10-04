package components

import csstype.Overflow
import icons.CloseIcon
import icons.CommentIcon
import icons.DeleteIcon
import kotlinx.js.timers.clearInterval
import kotlinx.js.timers.setInterval
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import payloads.CreatedComment
import react.*
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.strong
import react.dom.onChange
import react.router.dom.useSearchParams
import react.router.useLocation
import react.router.useNavigate
import react.router.useParams
import tools.confido.question.Comment
import tools.confido.question.Prediction
import tools.confido.question.Question
import utils.durationAgo
import utils.eventValue
import utils.now

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
    var predVisible by useState(false)
    var textAgo by useState("")

    val appState = useContext(AppStateContext)
    val currentUser = appState.state.session.name
    val canDelete = (props.comment.user == currentUser || appState.state.isAdmin) && !appState.stale

    // TODO generalize and fix the "no text on mount" issue
    useEffect(props.comment.timestamp) {
        fun setText() {
            textAgo = durationAgo(now() - props.comment.timestamp)
        }
        setText()
        val interval = setInterval(::setText,5000)

        cleanup {
            clearInterval(interval)
        }
    }

    Card {
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
                        lineHeight = 2.asDynamic()
                    }
                    strong {
                        +"Prediction: "
                    }
                when(predVisible) {
                    false -> Button {
                        +"Show"
                        size = Size.small
                        onClick = { predVisible = true }
                    }
                    true -> +(props.comment.prediction?.toString() ?: "")
                }
            }
            }
        }
    }
    Divider {}
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

    DialogContent {
        this.sx {
            this.overflowY = Overflow.visible
            this.flexGrow = 0.asDynamic()
        }
        TextField {
            fullWidth = true
            this.placeholder = "Comment..."
            this.margin = FormControlMargin.none
            this.value = content
            this.onChange = { content = it.eventValue() }
            this.disabled = stale
        }
        FormGroup {
            FormControlLabel {
                label = span.create {
                    +"Attach prediction "
                    props.prediction?.let {
                        Typography {
                            variant = TypographyVariant.body2
                            +it.toString()
                        }
                    }
                }
                control = Checkbox.create {
                    this.checked = attachPrediction
                    this.disabled = props.prediction == null || stale
                    this.onChange = { _, value -> attachPrediction = value }
                }
            }
        }
    }
    DialogActions {
        if (pendingSend)
            CircularProgress {
                size = 24
            }
        Button {
            +"Send"
            disabled = pendingSend || content.isEmpty() || stale
            onClick = {
                pendingSend = true
                val createdComment = CreatedComment(now(), content, attachPrediction)
                Client.postData("/add_comment/${props.id}", createdComment)
                content = ""
                pendingSend = false
                props.onSubmit?.invoke(createdComment)
            }
        }
    }
}

val QuestionComments = FC<QuestionCommentsProps> { props ->
    var deleteMode by useState(false)
    val count = props.comments.count()

    val location = useLocation().pathname
    val questionID = useParams()["questionID"]
    val open = location.endsWith("comments") && questionID == props.question.id

    val navigate = useNavigate()

    IconButton {
        onClick = { navigate("/questions/${props.question.id}/comments"); it.stopPropagation() }

        Badge {
            this.badgeContent = if (count > 0) ReactNode(count.toString()) else null
            this.color = BadgeColor.primary
            CommentIcon {}
        }
    }

    Dialog {
        this.open = open
        this.scroll = DialogScroll.paper
        fullScreen = true
        this.onClose = { _, _ -> navigate("/questions/${props.question.id}") }

        AppBar {
            this.position = AppBarPosition.relative
            Toolbar {
                IconButton {
                    CloseIcon {}
                    onClick = { navigate("/questions/${props.question.id}") }
                }
                Typography {
                    sx {
                        flexGrow = 1.asDynamic()
                    }
                    +"Comment"
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
            +props.question.name
        }
        DialogContent {
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