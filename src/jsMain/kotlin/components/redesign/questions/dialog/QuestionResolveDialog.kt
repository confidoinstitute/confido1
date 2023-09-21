package components.redesign.questions.dialog

import components.redesign.basic.Dialog
import components.redesign.forms.*
import components.showError
import hooks.useCoroutineLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionComplete
import react.FC
import react.Props
import react.dom.html.ButtonType
import react.useState
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.spaces.Value


external interface QuestionResolveDialogProps : Props {
    var question: Question
    var open: Boolean
    var onClose: (() -> Unit)?
}

val QuestionResolveDialog = FC<QuestionResolveDialogProps> { props ->
    var resolution by useState<Value?>(props.question.resolution)
    var resolutionValid by useState(props.question.resolution != null)
    var scoreTime by useState(props.question.effectiveSchedule.score)
    val tz = TimeZone.currentSystemDefault()

    val submitLock = useCoroutineLock()

    fun resolve() {
        // If we inherited schedule from room including score time and did not change
        // it, we want to keep it as inherited. However, if score time was changed in
        // resolve dialog, then we need to make a copy of the schedule for this question.
        val newSchedule = if (scoreTime == props.question.effectiveSchedule.score) props.question.schedule
                            else props.question.effectiveSchedule.copy(score = scoreTime)
        val question = props.question.copy(resolution = resolution, schedule = newSchedule).withState(QuestionState.RESOLVED)
        val editQuestion: EditQuestion = EditQuestionComplete(question)
        submitLock {
            Client.sendData(
                "${props.question.urlPrefix}/edit",
                editQuestion,
                onError = { showError(it) }) {
                props.onClose?.invoke()
            }
        }
    }

    Dialog {
        open = props.open
        onClose = { props.onClose?.invoke() }
        title = "Resolve this question"
        action = "Resolve"
        onAction = { resolve() }
        disabledAction = resolution == null || !resolutionValid || submitLock.running
        Form {
            onSubmit = { resolve() }
            FormSection {
                EditQuestionDialogResolution {
                    state = QuestionState.RESOLVED
                    space = props.question.answerSpace
                    value = resolution
                    valid = resolutionValid
                    valueRequired = true
                    this.onChange = {
                        resolution = it
                        resolutionValid = true
                    }
                    this.onError = {
                        resolution = null
                        resolutionValid = false
                    }
                }

                FormField {
                    this.title = "Score time"
                    this.comment = "Set a time from which predictions should be used for scoring and calibration curves." +
                                   " This is usually a compromise " +
                                   " between the forecasters having had enough time to think about the question and the " +
                                   " outcome not yet being too obvious. If no time is set, last prediction will be used."
                    DateTimeInput {
                        this.value = scoreTime?.toLocalDateTime(tz)
                        onChange = {
                            scoreTime = it?.toInstant(tz)
                        }
                    }
                }

                Button {
                    type = ButtonType.submit
                    disabled = resolution == null || !resolutionValid || submitLock.running
                    +"Resolve"
                }
            }
        }
    }
}

