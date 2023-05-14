package components.redesign.questions.dialog

import components.redesign.basic.Dialog
import components.redesign.forms.Button
import components.redesign.forms.Form
import components.redesign.forms.FormSection
import components.showError
import hooks.useCoroutineLock
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
    var resolution by useState<Value?>(null)
    var resolutionValid by useState(false)

    val submitLock = useCoroutineLock()

    fun resolve() {
        val question = props.question.copy(resolution = resolution).withState(QuestionState.RESOLVED)
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

                Button {
                    type = ButtonType.submit
                    disabled = submitLock.running
                    +"Resolve"
                }
            }
        }
    }
}

