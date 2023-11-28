package components.redesign.questions.dialog

import Client
import components.redesign.basic.BaseDialogProps
import components.redesign.basic.Dialog
import components.redesign.basic.dialogStateWrapper
import components.redesign.forms.*
import components.showError
import hooks.useCoroutineLock
import kotlinx.datetime.*
import kotlinx.js.jso
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionComplete
import react.FC
import react.dom.html.ButtonType
import react.useState
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.spaces.Value


external interface QuestionResolveDialogProps : BaseDialogProps{
    var question: Question
}

val QuestionResolveDialog = dialogStateWrapper(FC<QuestionResolveDialogProps>("QuestionResolveDialog") { props ->
    val question = props.question
    var resolution by useState(question.resolution)
    var resolutionValid by useState(question.resolution != null)
    var publish by useState(question.resolution == null || question.resolutionVisible)
    val submitLock = useCoroutineLock()
    var scoreTime by useState(props.question.effectiveSchedule.score)
    val tz = TimeZone.currentSystemDefault()

    fun resolve() {
        // If we inherited schedule from room including score time and did not change
        // it, we want to keep it as inherited. However, if score time was changed in
        // resolve dialog, then we need to make a copy of the schedule for this question.
        val newSchedule = if (scoreTime == props.question.effectiveSchedule.score) props.question.schedule
                            else props.question.effectiveSchedule.copy(score = scoreTime)
        val question = question.copy(resolution = resolution, schedule = newSchedule).let {
            if (publish) it.withState(QuestionState.RESOLVED)
            else it
        }
        // TODO: Make a dedicated edit mode for this to prevent overwrites!
        val editQuestion: EditQuestion = EditQuestionComplete(question)
        submitLock {
            Client.sendData(
                "${question.urlPrefix}/edit",
                editQuestion,
                onError = { showError(it) }) {
                props.onClose?.invoke()
            }
        }
    }

    Dialog {
        open = props.open
        onClose = { props.onClose?.invoke() }
        title = if (question.resolution != null) "Change resolution" else "Resolve this question"
        val buttonTitle = if (question.state != QuestionState.RESOLVED && publish) "Resolve" else "Save"
        val buttonDisabled = !resolutionValid || submitLock.running
        action = buttonTitle
        onAction = { resolve() }
        disabledAction = buttonDisabled
        Form {
            onSubmit = { resolve() }
            FormSection {
                InputFormField<Value, SpaceValueEntryProps>()() {
                    title = "Resolution"
                    comment = "The correct answer or actual outcome."
                    inputComponent = SpaceValueEntry
                    inputProps = jso {
                        space = question.answerSpace
                        value = resolution
                        this.onChange = { newVal, err->
                            resolution = newVal
                            resolutionValid = err == null
                        }
                    }
                }

                if (question.state != QuestionState.RESOLVED)
                FormSwitch {
                    label = "Publish resolution"
                    comment = "This will make resolution visible to all forecasters and prevent adding further ${question.predictionTerminology.plural}."
                    checked = publish
                    onChange = {  publish = it.target.checked }
                }
                if (publish)
                InputFormField<LocalDateTime, DateTimeInputProps>()() {
                    title = "Score time"
                    comment = "Set a time from which predictions should be used for computing calibration." +
                                   " This is usually a compromise " +
                                   " between the forecasters having had enough time to think about the question and the " +
                                   " outcome not yet being too obvious. If no time is set, the question will be excluded from calibration."
                    inputComponent = DateTimeInput
                    inputProps = jso {
                        value = scoreTime?.toLocalDateTime(tz)
                        defaultTime = LocalTime.fromSecondOfDay(0)
                        onChange = { newVal, err->
                            scoreTime = newVal?.toInstant(tz)
                        }
                    }
                }

                Button {
                    type = ButtonType.submit
                    disabled = buttonDisabled
                    +buttonTitle
                }
            }
        }
    }
})

