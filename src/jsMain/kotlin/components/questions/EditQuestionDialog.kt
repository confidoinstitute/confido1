package components.questions

import Client
import components.rooms.RoomContext
import kotlinx.datetime.LocalDate
import mui.material.*
import react.*
import react.dom.html.InputType
import react.dom.onChange
import tools.confido.payloads.EditQuestion
import tools.confido.payloads.EditQuestionComplete
import tools.confido.question.Question
import tools.confido.spaces.*
import tools.confido.utils.*
import utils.eventNumberValue
import utils.eventValue
import kotlin.js.Date
import kotlin.math.floor

external interface EditAnswerSpaceProps<S: Space> : Props {
    var space : S
    var disabled: Boolean
    var onChange: ((S?) -> Unit)?
}

val EditNumericSpace = FC<EditAnswerSpaceProps<NumericSpace>> { props ->
    var minValue by useState(props.space.min)
    var maxValue by useState(props.space.max)
    var unit by useState(props.space.unit)

    useEffect(minValue, maxValue, unit) {
        if (minValue.isNaN() || maxValue.isNaN() || maxValue <= minValue)
            props.onChange?.invoke(null)
        else
            props.onChange?.invoke(NumericSpace(minValue, maxValue, unit = unit))
    }

    FormGroup {
        TextField {
            margin = FormControlMargin.dense
            type = InputType.number
            value = minValue
            label = ReactNode("Min")
            error = minValue.isNaN()
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
            error = maxValue.isNaN() || (maxValue <= minValue)
            disabled = props.disabled
            if (maxValue <= minValue)
                helperText = ReactNode("Inconsistent range.")
            onChange = {
                maxValue = it.eventNumberValue()
            }
        }
        TextField {
            margin = FormControlMargin.dense
            value = unit
            label = ReactNode("Unit")
            disabled = props.disabled
            onChange = {
                unit = it.eventValue()
            }
        }
    }
}

val EditDaysAnswerSpace = FC<EditAnswerSpaceProps<NumericSpace>> { props ->
    var minValue by useState(LocalDate.fromUnix(props.space.min).toString())
    var maxValue by useState(LocalDate.fromUnix(props.space.max).toString())
    val minDate = try { LocalDate.parse(minValue) } catch  (e: Exception) { null }
    val maxDate = try { LocalDate.parse(maxValue) } catch  (e: Exception) { null }

    useEffect(minValue, maxValue) {
        if (maxDate != null && minDate != null && maxDate > minDate)
            props.onChange?.invoke(NumericSpace.fromDates(minDate, maxDate))
        else
            props.onChange?.invoke(null)
    }

    FormGroup {
        TextField {
            margin = FormControlMargin.dense
            type = InputType.date
            value = minValue
            label = ReactNode("First day")
            error = minDate == null
            disabled = props.disabled
            onChange = {
                minValue = it.eventValue()
            }
        }
        TextField {
            margin = FormControlMargin.dense
            type = InputType.date
            value = maxValue
            label = ReactNode("Last day")
            error = maxDate == null || (minDate != null && maxDate <= minDate)
            disabled = props.disabled
            if (maxDate != null && minDate != null && maxDate <= minDate)
                helperText = ReactNode("Inconsistent range.")
            onChange = {
                maxValue = it.eventValue()
            }
        }
    }
}

external interface EditQuestionDialogProps : Props {
    var question: Question?
    var open: Boolean
    var onClose: (() -> Unit)?
}

val EditQuestionDialog = FC<EditQuestionDialogProps> { props ->
    val q = props.question
    val room = useContext(RoomContext)

    var id by useState(q?.id ?: "")
    var name by useState(q?.name ?: "")
    var answerSpace : Space? by useState(q?.answerSpace)
    var visible by useState(q?.visible ?: true)
    var enabled by useState(q?.enabled ?: true)
    var predictionsVisible by useState(q?.predictionsVisible ?: false)
    var resolved by useState(q?.resolved ?: false)
    val htmlId = useId()

    val answerSpaceEditable = (q == null)

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
        if (error) return
        val question = Question(id, name, answerSpace!!, visible, enabled, predictionsVisible, resolved)
        val editQuestion: EditQuestion = EditQuestionComplete(question, room.id)

        // TODO Make better REST API, as it now requires ID to be sent in URL path
        Client.postData("/edit_question/edit", editQuestion)
        props.onClose?.invoke()
    }

    val answerSpaceType = when(answerSpace) {
        is BinarySpace -> "binary"
        is NumericSpace ->
            if ((answerSpace as NumericSpace).representsDays) "day" else "numeric"
        else -> ""
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
                    disabled = !answerSpaceEditable
                    error = errorEmptyAnswerSpace
                    onChange = { event, _ ->
                        when (event.target.value) {
                            "" -> answerSpace = null
                            "binary" -> answerSpace = BinarySpace
                            "numeric" ->
                                if (answerSpace.let { it !is NumericSpace || it.representsDays }) {
                                    answerSpace = NumericSpace(0.0, 1.0)
                                    errorBadAnswerSpace = false
                                }
                            "day" ->
                                if (answerSpace.let {  it !is NumericSpace || !it.representsDays } ){
                                    val today = ((unixNow() / 86400).toInt() * 86400).toDouble()
                                    answerSpace = NumericSpace( today, today+30, representsDays = true)
                                    errorBadAnswerSpace = false
                                }
                        }
                        errorEmptyAnswerSpace = answerSpace == null
                    }
                    mapOf("" to "Choose...", "binary" to "Binary", "numeric" to "Numeric", "day" to "Day").map { (value, label) ->
                        MenuItem {
                            this.value = value
                            +label
                        }
                    }
                }
                if (!answerSpaceEditable) FormHelperText {
                    +"Answer type cannot be changed now as predictions have already been made."
                }
            }
            if (answerSpace is NumericSpace) {
                val numericAnswerSpace = answerSpace as NumericSpace
                val component = if (numericAnswerSpace.representsDays) EditDaysAnswerSpace else EditNumericSpace
                component {
                    this.space = space
                    disabled = !answerSpaceEditable
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
                    label = ReactNode("Open for predictions")
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
            if (q != null) {
                Button {
                    onClick = {props.onClose?.invoke()}
                    color = ButtonColor.error
                    +"Delete"
                }
            }
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