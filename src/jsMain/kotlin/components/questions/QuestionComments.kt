package components.questions

import components.Comment
import components.CommentInput
import components.CommentInputVariant
import components.DialogCloseButton
import csstype.number
import csstype.pct
import icons.CommentIcon
import mui.material.*
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.span
import tools.confido.question.Comment
import tools.confido.question.Prediction
import tools.confido.question.Question

external interface QuestionCommentsDialogProps : Props {
    var question: Question
    var prediction: Prediction?
    var comments: Map<String, Comment>
    var open: Boolean
    var onClose: (()->Unit)?
}

external interface QuestionCommentsButtonProps : Props {
    var onClick: (()->Unit)?
    var numComments: Int
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
            props.comments.entries.sortedByDescending { it.value.timestamp }.map {
                Comment {
                    key = it.key
                    comment = it.value
                }
            }
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