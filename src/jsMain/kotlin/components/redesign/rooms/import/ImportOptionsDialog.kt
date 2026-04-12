package components.redesign.rooms.import

import components.redesign.basic.*
import components.redesign.forms.*
import payloads.requests.ImportQuestionOptions
import react.*
import tools.confido.question.CommentVisibility
import tools.confido.question.GroupPredictionVisibility
import tools.confido.question.QuestionState
import web.cssom.*
import emotion.react.css

external interface ImportOptionsDialogProps : BaseDialogProps {
    var options: ImportQuestionOptions
    var onOptionsChange: (ImportQuestionOptions) -> Unit
}

val ImportOptionsDialog = FC<ImportOptionsDialogProps> { props ->
    val options = props.options
    val gpv = GroupPredictionVisibility.fromBooleans(options.groupPredVisible, options.groupPredRequirePrediction)

    Dialog {
        open = props.open
        onClose = props.onClose
        title = "Question options"
        action = "Done"
        onAction = { props.onClose?.invoke() }

        FormSection {
            title = "Initial state"
            OptionGroup<QuestionState>()() {
                value = if (!options.visible) QuestionState.DRAFT
                        else if (options.open) QuestionState.OPEN
                        else QuestionState.CLOSED
                this.options = listOf(
                    QuestionState.DRAFT to "Draft",
                    QuestionState.OPEN to "Open",
                )
                onChange = { newState: QuestionState ->
                    val newOpts = when (newState) {
                        QuestionState.DRAFT -> options.copy(visible = false, open = false)
                        QuestionState.OPEN -> options.copy(visible = true, open = true)
                        else -> options
                    }
                    props.onOptionsChange(newOpts)
                }
            }
        }

        FormSection {
            title = "Anchoring"
            RadioGroup<GroupPredictionVisibility>()() {
                this.title = "Group answer visible to"
                value = gpv
                this.options = listOf(
                    GroupPredictionVisibility.EVERYONE to "all room members",
                    GroupPredictionVisibility.ANSWERED to "those who answered",
                    GroupPredictionVisibility.MODERATOR_ONLY to "moderators only",
                )
                onChange = { newGpv: GroupPredictionVisibility ->
                    props.onOptionsChange(options.copy(
                        groupPredVisible = newGpv.groupPredVisible,
                        groupPredRequirePrediction = newGpv.groupPredRequirePrediction,
                    ))
                }
            }
        }

        FormSection {
            title = "Comments"
            FormSwitch {
                label = "Allow comments"
                comment = "Whether participants can comment on imported questions"
                checked = options.allowComments
                onChange = {
                    props.onOptionsChange(options.copy(allowComments = it.target.checked))
                }
            }
            if (options.allowComments) {
                RadioGroup<CommentVisibility>()() {
                    title = "Comments visible to"
                    value = options.commentVisibility
                    this.options = listOf(
                        CommentVisibility.EVERYONE to "all room members",
                        CommentVisibility.ANSWERED to "those who answered",
                        CommentVisibility.MODERATOR_ONLY to "moderators only",
                    )
                    onChange = { cv: CommentVisibility ->
                        props.onOptionsChange(options.copy(commentVisibility = cv))
                    }
                }
            }
        }
    }
}
