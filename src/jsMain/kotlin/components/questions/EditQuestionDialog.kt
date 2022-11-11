package components.questions

import Client
import components.AppStateContext
import components.ValueEntry
import components.rooms.RoomContext
import csstype.px
import hooks.EditEntityDialogProps
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import mui.material.*
import mui.system.sx
import react.*
import react.dom.html.InputType
import react.dom.onChange
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionComplete
import tools.confido.question.Question
import tools.confido.refs.HasId
import tools.confido.spaces.*
import tools.confido.utils.*
import utils.eventNumberValue
import utils.eventValue
import utils.themed
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
            label = ReactNode("Unit (optional)")
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

external interface EditQuestionDialogProps : EditEntityDialogProps<Question> {
}

val EditQuestionDialog = FC<EditQuestionDialogProps> { props ->
    val q = props.entity
    val (_, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    // Question values
    var id by useState(q?.id ?: "")
    var name by useState(q?.name ?: "")
    var description by useState(q?.description ?: "")
    var answerSpace : Space? by useState(q?.answerSpace)
    var visible by useState(q?.visible ?: true)
    var enabled by useState(q?.open ?: true)
    var groupPredVisible by useState(q?.groupPredVisible ?: false)
    var resolved by useState(q?.resolution != null)
    var resolutionVisible by useState((q?.resolutionVisible ?: false) && (q?.resolution != null))
    var resolution by useState(q?.resolution)

    val htmlId = useId()
    val answerSpaceEditable = q == null || q.numPredictions == 0
    useLayoutEffect(answerSpaceEditable) {
        if (!answerSpaceEditable)
            answerSpace = q?.answerSpace
    }

    // Errors
    var errorEmptyName by useState(false)
    var errorEmptyAnswerSpace by useState(false)
    var errorBadAnswerSpace by useState(false)
    var errorInvalidResolution by useState(false)

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
        if (resolved && resolution == null) {
            errorInvalidResolution = true
            error = true
        }
        console.log("resolved: $resolved resolution: $resolution")
        if (error) return
        val question = Question(
            id = id,
            name = name,
            description = description,
            answerSpace = answerSpace!!,
            visible = visible,
            open = enabled,
            groupPredVisible = groupPredVisible,
            resolutionVisible = resolved && resolutionVisible,
            resolution = if (resolved) resolution else null,
        )

        if (props.entity == null) {
            Client.postData("/rooms/${room.id}/questions/add", question)
        } else {
            val editQuestion: EditQuestion = EditQuestionComplete(question)
            Client.postData("/questions/${id}/edit", editQuestion)
        }
        props.onClose?.invoke()
    }

    fun deleteQuestion() {
        CoroutineScope(EmptyCoroutineContext).launch {
            Client.httpClient.delete("/questions/$id")
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
                key = "resolutionHint"
                    +"Try and make your question specific and resolvable - so that after the event, everyone will agree on what the outcome is."
            }
            TextField {
                key = "questionText"
                value = name
                label = ReactNode("Title")
                fullWidth = true
                onChange = { name = it.eventValue(); errorEmptyName = false }
                margin = FormControlMargin.normal
                error = errorEmptyName
            }
            DialogContentText {
                key = "titleDetailHint"
                    +"The question title should cover the main topic, while the description can explain the more detailed information."
            }
            TextField {
                key = "description"
                value = description
                label = ReactNode("Description (optional)")
                multiline = true
                rows = 2
                fullWidth = true
                margin = FormControlMargin.normal
                onChange = { description = it.eventValue() }
            }
            FormControl {
                key = "answerSpaceType"
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
                    mapOf("" to "Choose...", "binary" to "Yes/no", "numeric" to "Number", "day" to "Date").map { (value, label) ->
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
                    key = "numericAnswerSpaceEdit"
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
                key = "flagsGroup"
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
                        checked = groupPredVisible
                    }
                    onChange = {_, value -> groupPredVisible = value}
                }
                if (answerSpace != null) {
                    FormControlLabel {
                        key = "resolvedCheckbox"
                        label = ReactNode("Resolved (outcome/correct answer is known)")
                        control = Checkbox.create {
                            checked = resolved
                        }
                        onChange = { _, value -> resolved = value }
                    }
                }
            }

            answerSpace?.let {answerSpace ->
                if (resolved) {
                    Box {
                        key = "resolvedBox"
                        sx {
                            marginTop = themed(1)
                            marginLeft = 42.px
                        }
                        ValueEntry  {
                            this.key = "resolvedValueEntry"
                            this.label = "Resolution"
                            this.space = answerSpace
                            this.value = resolution
                            this.onChange = { value -> resolution = value  }
                        }
                        FormGroup {
                            FormControlLabel {
                                label = ReactNode("Resolution visible to forecasters")
                                control = Checkbox.create {
                                    checked = resolutionVisible
                                }
                                onChange = {_, value -> resolutionVisible = value}
                            }
                        }
                    }
                }
            }
        }

        DialogActions {
            if (q != null) {
                DeleteQuestionConfirmation {
                    this.onDelete = { deleteQuestion() ; props.onClose?.invoke()}
                    this.confirmDelete = q.visible
                    this.hasPrediction = q.numPredictions > 0
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
