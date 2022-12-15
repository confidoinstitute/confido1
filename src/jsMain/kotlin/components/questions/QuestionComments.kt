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

external interface QuestionCommentsProps : Props {
    var question: Question
    var prediction: Prediction?
    var comments: Map<String, Comment>
}

val QuestionComments = FC<QuestionCommentsProps> { props ->
    val count = props.comments.count()
    var open by useState(false)


    Tooltip {
        title = ReactNode("Question comments")
        arrow = true
        span {
            IconButton {
                onClick = { open = true; it.stopPropagation() }

                Badge {
                    this.badgeContent = if (count > 0) ReactNode(count.toString()) else null
                    this.color = BadgeColor.secondary
                    CommentIcon {}
                }
            }
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

        DialogTitle {
            +"Comments"
            Typography {
                +props.question.name
            }
            DialogCloseButton {
                onClose = { open = false }
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