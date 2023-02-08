package components.questions

import components.*
import csstype.number
import csstype.pct
import hooks.useWebSocket
import icons.CommentIcon
import mui.material.*
import mui.system.sx
import payloads.responses.CommentInfo
import payloads.responses.WSData
import payloads.responses.WSError
import react.*
import react.dom.html.ReactHTML.span
import tools.confido.question.Prediction
import tools.confido.question.Question

external interface QuestionCommentsListProps : Props {
    var question: Question
    var count: Int
}

external interface QuestionCommentsDialogProps : Props {
    var question: Question
    var numComments: Int
    var prediction: Prediction?
    var open: Boolean
    var onClose: (()->Unit)?
}

external interface QuestionCommentsButtonProps : Props {
    var onClick: (()->Unit)?
    var numComments: Int
}

val QuestionCommentsList = FC<QuestionCommentsListProps> {props ->
    val comments = useWebSocket<Map<String, CommentInfo>>("/state${props.question.urlPrefix}/comments")

    if (comments is WSError) {
        Alert {
            severity = AlertColor.error
            +comments.prettyMessage
        }
    }

    when(comments) {
        is WSData -> comments.data.entries.sortedByDescending { it.value.comment.timestamp }.map {
            Comment {
                key = it.key
                commentInfo = it.value
            }
        }
        else -> (0 until props.count).map { CommentSkeleton {} }
    }
}

val QuestionCommentsDialog = FC<QuestionCommentsDialogProps> { props->
    Dialog {
        this.open = props.open
        this.scroll = DialogScroll.paper
        this.fullWidth = true
        this.maxWidth = "lg"
        this.onClose = { _, _ -> props.onClose?.invoke() }
        sx {
            ".MuiDialog-paper" {
                minHeight = 50.pct
            }
        }

        DialogTitle {
            +"Comments"
            Typography {
                +props.question.name
            }
            DialogCloseButton {
                onClose = { props.onClose?.invoke() }
            }
        }
        DialogContent {
            sx {
                flexGrow = number(1.0)
            }
            this.dividers = true
            QuestionCommentsList { question = props.question; count = props.numComments }
        }
        CommentInput {
            id = props.question.id
            prediction = props.prediction
            variant = CommentInputVariant.QUESTION
        }
    }
}

val QuestionCommentsButton = FC<QuestionCommentsButtonProps> { props ->
    val count = props.numComments

    Tooltip {
        title = ReactNode("Question comments")
        arrow = true
        span {
            IconButton {
                onClick = { props.onClick?.invoke(); it.stopPropagation() }

                Badge {
                    this.badgeContent = if (count > 0) ReactNode(count.toString()) else null
                    this.color = BadgeColor.secondary
                    CommentIcon {}
                }
            }
        }
    }

}
