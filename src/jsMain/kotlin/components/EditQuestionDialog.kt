package components

import kotlinx.browser.document
import kotlinx.browser.window
import mui.material.*
import org.w3c.dom.HTMLInputElement
import react.*
import react.dom.events.ChangeEvent
import react.dom.events.FormEvent
import react.dom.html.InputType
import react.dom.html.ReactHTML.input
import react.dom.onChange
import tools.confido.question.AnswerSpace
import tools.confido.question.BinaryAnswerSpace
import tools.confido.question.NumericAnswerSpace
import tools.confido.question.Question
import utils.eventNumberValue
import utils.eventValue
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KMutableProperty

external interface EditAnswerSpaceProps<T: AnswerSpace> : Props {
    var minValue: Double
    var maxValue: Double
    var disabled: Boolean
    var error: Boolean
    var onChange: ((T) -> Unit)?
}

val EditNumericAnswerSpace = FC<EditAnswerSpaceProps<NumericAnswerSpace>> {props ->
    var minValue by useState(props.minValue)
    var maxValue by useState(props.maxValue)

    useEffect(minValue, maxValue) {
        props.onChange?.invoke(NumericAnswerSpace(32, minValue, maxValue))
    }

    // TODO error handling
    FormGroup {
        TextField {
            margin = FormControlMargin.dense
            type = InputType.number
            value = minValue
            label = ReactNode("Min")
            error = props.error
            disabled = props.disabled
            onChange = {
                minValue = it.eventNumberValue()
            }
        }
        TextField {
            margin = FormControlMargin.dense
            type = InputType.number
            value = maxValue
            label = ReactNode("Max")
            error = props.error
            disabled = props.disabled
            if (props.error)
                helperText = ReactNode("Inconsistent range.")
            onChange = {
                maxValue = it.eventNumberValue()
            }
        }
    }
}

// TODO EditDateAnswerSpace

external interface EditQuestionDialogProps : Props {
    var question: Question?
    var open: Boolean
    var onClose: (() -> Unit)?
}

val EditQuestionDialog = FC<EditQuestionDialogProps> {props ->
    val q = props.question
    console.log(q)
    var id by useState(q?.id ?: "")
    var name by useState(q?.name ?: "")
    var answerSpace : AnswerSpace? by useState(q?.answerSpace)
    var visible by useState(q?.visible ?: true)
    var enabled by useState(q?.enabled ?: true)
    var predictionsVisible by useState(q?.predictionsVisible ?: false)
    var resolved by useState(q?.resolved ?: false)
    val htmlId = useId()

    var errorEmptyName by useState(false)
    var errorEmptyAnswerSpace by useState(false)
    var errorBadAnswerSpace by useState(false)

    // TODO better error handling?
    fun submitQuestion() {
        var error = false
        if (answerSpace == null) {
            errorEmptyAnswerSpace = true
            error = true
        }
        if (name.isEmpty()) {
            errorEmptyName = true
            error = true
        }
        if (answerSpace?.verifyParams() != true) {
            errorBadAnswerSpace = true
            error = true
        }
        if (error) return
        window.alert(Question(id, name, answerSpace!!, visible, enabled, predictionsVisible, resolved).toString())
    }

    val answerSpaceType = when(answerSpace) {
        null -> ""
        is BinaryAnswerSpace -> "binary"
        is NumericAnswerSpace -> "numeric"
    }

    Dialog {
        open = props.open
        onClose = {_, _ -> props.onClose?.invoke() }
        DialogTitle {
            if (q != null) +"Edit question" else +"Add question"
        }
        DialogContent {
            DialogContentText {
                +"Try and make your question specific and resolvable - so that after the event, everyone will agree on what the outcome is."
            }
            TextField {
                value = name
                label = ReactNode("Question")
                multiline = true
                maxRows = 4
                fullWidth = true
                onChange = { name = it.eventValue(); errorEmptyName = false }
                margin = FormControlMargin.normal
                error = errorEmptyName
            }
            FormControl {
                this.fullWidth = true
                InputLabel {
                    this.id = htmlId +"edit_question_label"
                    this.error = errorEmptyAnswerSpace
                    +"Answer Type"
                }
                Select {
                    this.id = htmlId + "edit_question"
                    labelId = htmlId + "edit_question_label"
                    value = answerSpaceType.asDynamic()
                    label = ReactNode("Answer Type")
                    placeholder = "Choose..."
                    disabled = q != null
                    error = errorEmptyAnswerSpace
                    onChange = { event, _ ->
                        when (event.target.value) {
                            "" -> answerSpace = null
                            "binary" -> answerSpace = BinaryAnswerSpace()
                            "numeric" -> answerSpace = NumericAnswerSpace(32, 0.0, 1.0)
                        }
                        errorEmptyAnswerSpace = false
                    }
                    mapOf("" to "Choose...", "binary" to "Binary", "numeric" to "Numeric").map { (value, label) ->
                        MenuItem {
                            this.value = value
                            +label
                        }
                    }
                }
            }
            if (answerSpace is NumericAnswerSpace) {
                val numericAnswerSpace = answerSpace as NumericAnswerSpace
                EditNumericAnswerSpace {
                    minValue = numericAnswerSpace.min
                    maxValue = numericAnswerSpace.max
                    disabled = q != null
                    error = errorBadAnswerSpace
                    onChange = {
                        answerSpace = it
                        errorBadAnswerSpace = false
                    }
                }
            }

            // TODO is there a way to create a function that allows assignment to its parameter?
            FormGroup {
                FormControlLabel {
                    label = ReactNode("Visible to participants")
                    control = Checkbox.create {
                        checked = visible
                    }
                    onChange = {_, value -> visible = value}
                }
                FormControlLabel {
                    label = ReactNode("Predictions enabled")
                    control = Checkbox.create {
                        checked = enabled
                    }
                    onChange = {_, value -> enabled = value}
                }
                FormControlLabel {
                    label = ReactNode("Group predictions visible")
                    control = Checkbox.create {
                        checked = predictionsVisible
                    }
                    onChange = {_, value -> predictionsVisible = value}
                }
                FormControlLabel {
                    label = ReactNode("Resolution visible")
                    control = Checkbox.create {
                        checked = resolved
                    }
                    onChange = {_, value -> resolved = value}
                }
            }

        }
        DialogActions {
            Button {
                onClick = {props.onClose?.invoke()}
                +"Cancel"
            }
            Button {
                onClick = {submitQuestion()}
                if (q != null) +"Edit" else +"Add"
            }
        }
    }
}