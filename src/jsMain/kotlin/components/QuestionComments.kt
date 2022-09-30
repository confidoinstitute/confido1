package components

import csstype.FlexGrow
import csstype.Overflow
import icons.BarChart
import icons.CloseIcon
import icons.CommentIcon
import icons.DeleteIcon
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.js.timers.clearInterval
import kotlinx.js.timers.setInterval
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.material.transitions.TransitionProps
import mui.system.sx
import payloads.CreatedComment
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.strong
import react.dom.onChange
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
    val currentUser = appState.session.name
    val canDelete = props.comment.user == currentUser || appState.isAdmin

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
                    this.disabled = props.prediction == null
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
            disabled = pendingSend || content.isEmpty()
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
    var open by useState(false)
    var deleteMode by useState(false)
    val count = props.comments.count()

    IconButton {
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
        fullScreen = true
        AppBar {
            this.position = AppBarPosition.relative
            Toolbar {
                IconButton {
                    CloseIcon {}
                    onClick = { open = false; it.stopPropagation() }
                }
                Typography {
                    sx {
                        flexGrow = 1.asDynamic()
                    }
                    +"Comments"
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