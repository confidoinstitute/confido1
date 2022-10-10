package components.questions

import Client
import components.AppStateContext
import components.rooms.RoomContext
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import mui.material.*
import react.*
import react.dom.html.InputType
import react.dom.onChange
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionComplete
import tools.confido.question.Question
import tools.confido.spaces.*
import tools.confido.utils.*
import utils.eventNumberValue
import utils.eventValue
import kotlin.coroutines.EmptyCoroutineContext

external interface DeleteQuestionConfirmationProps : Props {
    var confirmDelete: Boolean
    var hasPrediction: Boolean
    var onDelete: (() -> Unit)?
}

val DeleteQuestionConfirmation = FC<DeleteQuestionConfirmationProps> { props ->
    var open by useState(false)
    Button {
        onClick = {if (props.confirmDelete) open = true else {props.onDelete?.invoke()} }
        color = ButtonColor.error
        +"Delete"
    }

    Dialog {
        this.open = open
        onClose = { _, _ -> open = false }
        DialogTitle {
            +"Delete question"
        }
        DialogContent {
            DialogContentText {
                +"This action is irreversible. Are you sure?"
                if (props.hasPrediction) +" Deleting this question will also forget all its received predictions."
            }
        }
        DialogActions {
            Button {
                onClick = {props.onDelete?.invoke(); open = false}
                color = ButtonColor.error
                +"Delete"
            }
            Button {
                onClick = {open = false}
                +"Cancel"
            }
        }
    }
}

external interface EditAnswerSpaceProps<S: Space> : Props {
    var space : S
    var disabled: Boolean
    var onChange: ((S?) -> Unit)?
}

val EditNumericSpace = FC<EditAnswerSpaceProps<NumericSpace>> { props ->
    var minValue by useState(props.space.min)
    var maxValue by useState(props.space.max)
    var unit by useState(props.space.unit)

    var strMinValue by useState(minValue.toString())
    var strMaxValue by useState(maxValue.toString())

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
            value = strMinValue
            label = ReactNode("Min")
            error = minValue.isNaN()
            disabled = props.disabled
            onChange = {
                strMinValue = it.eventValue()
                minValue = it.eventNumberValue()
            }
        }
        TextField {
            margin = FormControlMargin.dense
            type = InputType.number
            value = strMaxValue
            label = ReactNode("Max")
            error = maxValue.isNaN() || (maxValue <= minValue)
            disabled = props.disabled
            if (maxValue <= minValue)
                helperText = ReactNode("Inconsistent range.")
            onChange = {
                strMaxValue = it.eventValue()
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
    val (_, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    // Question values
    var id by useState(q?.id ?: "")
    var name by useState(q?.name ?: "")
    var answerSpace : Space? by useState(q?.answerSpace)
    var visible by useState(q?.visible ?: true)
    var enabled by useState(q?.enabled ?: true)
    var predictionsVisible by useState(q?.predictionsVisible ?: false)
    var resolved by useState(q?.resolved ?: false)

    val htmlId = useId()
    val answerSpaceEditable = (q == null)

    // Errors
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

    fun deleteQuestion() {
        CoroutineScope(EmptyCoroutineContext).launch {
            Client.httpClient.delete("/delete_question/$id")
        }
    }

    val answerSpaceType = when(val space = answerSpace) {
        BinarySpace -> "binary"
        is NumericSpace ->
            if (space.representsDays) "day" else "numeric"
        else -> ""
    }
    useEffect(answerSpaceType) {
        errorEmptyAnswerSpace = false
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
                                }
                            "day" ->
                                if (answerSpace.let {  it !is NumericSpace || !it.representsDays } ){
                                    val today = ((unixNow() / 86400) * 86400).toDouble()
                                    answerSpace = NumericSpace( today, today+86400, representsDays = true)
                                }
                        }
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
                    this.space = numericAnswerSpace
                    disabled = !answerSpaceEditable
                    onChange = {numericSpace ->
                        numericSpace?.let {
                            answerSpace = it
                            errorBadAnswerSpace = false
                        } ?: run {
                            errorBadAnswerSpace = true
                        }
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
                DeleteQuestionConfirmation {
                    this.onDelete = { deleteQuestion() ; props.onClose?.invoke()}
                    this.confirmDelete = q.visible
                    // TODO add real logic
                    this.hasPrediction = q.enabled
                }
            }
            Button {
                onClick = {props.onClose?.invoke()}
                +"Cancel"
            }
            Button {
                onClick = {submitQuestion()}
                disabled = stale
                if (q != null) +"Edit" else +"Add"
            }
        }
    }
}