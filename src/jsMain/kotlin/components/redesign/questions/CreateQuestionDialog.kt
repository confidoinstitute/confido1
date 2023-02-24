package components.redesign.questions

import components.redesign.basic.Dialog
import components.redesign.basic.Stack
import components.redesign.forms.*
import components.redesign.forms.Button
import components.rooms.RoomContext
import components.showError
import csstype.*
import emotion.react.css
import hooks.useCoroutineLock
import kotlinx.datetime.LocalDate
import react.*
import react.dom.html.InputType
import tools.confido.spaces.*

external interface CreateQuestionDialogProps : Props {
    var open: Boolean
    var onClose: (() -> Unit)?
}

private enum class QuestionType {
    BINARY,
    NUMERIC,
    DATE,
}

private enum class GroupPredictionVisibility {
    EVERYONE,
    ANSWERED,
    MODERATOR_ONLY,
}

private enum class QuestionStatus {
    OPEN,
    CLOSED,
    RESOLVED,
}

val CreateQuestionDialog = FC<CreateQuestionDialogProps> { props ->
    val room = useContext(RoomContext)

    var questionType by useState(QuestionType.BINARY)
    var questionStatus by useState(QuestionStatus.OPEN)
    var questionTitle by useState("")
    var questionDescription by useState("")
    var unit by useState("")
    var minValue by useState("")
    var maxValue by useState("")
    var minDateValue by useState("")
    var maxDateValue by useState("")
    var groupPredictionVisibility by useState(GroupPredictionVisibility.EVERYONE)
    var isVisible by useState(true)
    var allowComments by useState(true)
    var binaryResolution by useState<Boolean?>(null)
    var dateResolution by useState<LocalDate?>(null)
    var numericResolution by useState<Double?>(null)
    var isSensitive by useState(false)

    val answerSpace: Space? = when (questionType) {
        QuestionType.BINARY -> BinarySpace
        QuestionType.NUMERIC -> {
            var space = NumericSpace()
            if (minValue.isNotBlank()) {
                try {
                    space = space.copy(min = minValue.toDouble())
                } catch (e: NumberFormatException) {
                    // TODO: Handle error
                }
            }
            if (maxValue.isNotBlank()) {
                try {
                    space = space.copy(max = maxValue.toDouble())
                } catch (e: NumberFormatException) {
                    // TODO: Handle error
                }
            }
            if (unit.isNotBlank()) {
                space = space.copy(unit = unit)
            }
            space
        }
        QuestionType.DATE -> {
            val minDate = if (minValue.isNotBlank()) {
                try {
                    LocalDate.parse(minValue)
                } catch (e: Exception) {
                    null
                }
            } else {
                // TODO: Solve unbounded date ranges properly
                LocalDate.fromEpochDays(0)
            }
            val maxDate = if (maxValue.isNotBlank()) {
                try {
                    LocalDate.parse(maxValue)
                } catch (e: Exception) {
                    null
                }
            } else {
                // TODO: Solve unbounded date ranges properly
                LocalDate.fromEpochDays(2000 * 365)
            }
            if (minDate != null && maxDate != null) {
                NumericSpace.fromDates(minDate, maxDate)
            } else {
                // TODO: Handle error
                null
            }
        }
    }

    val resolution: Value? = when (answerSpace) {
        is BinarySpace -> binaryResolution?.let { BinaryValue(it) }
        is NumericSpace -> {
            when (answerSpace.representsDays) {
                false -> numericResolution?.let {
                    if (answerSpace.checkValue(it)) {
                        NumericValue(answerSpace, it)
                    } else {
                        null
                    }
                }

                true -> dateResolution?.let {
                    val nv = it.toEpochDays() * 86400.0
                    if (answerSpace.checkValue(nv)) {
                        NumericValue(answerSpace, nv)
                    } else {
                        null
                    }
                }
            }
        }
        null -> null
    }

    val submit = useCoroutineLock()
    fun submitQuestion() = submit {
        val question = tools.confido.question.Question(
            id = "",
            name = questionTitle,
            description = questionDescription,
            answerSpace = answerSpace!!,
            visible = isVisible,
            open = questionStatus == QuestionStatus.OPEN,
            groupPredVisible = when (groupPredictionVisibility) {
                GroupPredictionVisibility.EVERYONE -> true
                GroupPredictionVisibility.ANSWERED -> TODO() // No backend support
                GroupPredictionVisibility.MODERATOR_ONLY -> false
            },
            resolutionVisible = questionStatus == QuestionStatus.RESOLVED,
            resolution = resolution,
        )

        Client.sendData(
            "${room.urlPrefix}/questions/add",
            question,
            onError = { showError?.invoke(it) }) { props.onClose?.invoke() }
    }

    Dialog {
        open = props.open
        onClose = props.onClose
        title = "Create a question"
        action = "Create"
        onAction = { submitQuestion() }

        Form {
            FormSection {
                title = "Question"
                FormField {
                    title = "Title"
                    required = true
                    comment = "The question title should cover the main topic. Try and make your question specific and resolvable – so that after the event, everyone will agree on what the outcome is."
                    TextInput {
                        placeholder = "Enter the question title"
                        value = questionTitle
                        onChange = { e -> questionTitle = e.target.value }
                    }
                }
                FormField {
                    title = "Description"
                    comment = "The description should contain all resolution criteria."
                    TextInput {
                        placeholder = "Tell others more about the question"
                        value = questionDescription
                        onChange = { e -> questionDescription = e.target.value }
                    }
                }
            }

            FormSection {
                title = "Answer"
                FormField {
                    title = "Type"
                    OptionGroup<QuestionType>()() {
                        options = listOf(
                            QuestionType.BINARY to "Yes/No",
                            QuestionType.NUMERIC to "Number",
                            QuestionType.DATE to "Date"
                        )
                        defaultValue = QuestionType.BINARY
                        value = questionType
                        onChange = { type -> questionType = type }
                    }
                    comment = when (questionType) {
                        QuestionType.BINARY -> "The question asks whether something is true."
                        QuestionType.NUMERIC -> "The answer to this question is a number."
                        QuestionType.DATE -> "The answer to this question is a date."
                    }
                }
                when (questionType) {
                    QuestionType.BINARY -> {}
                    QuestionType.NUMERIC -> {
                        FormField {
                            title = "Range"
                            comment = "Set range only if the answers out of it do not make sense (e.g. a negative duration of an event). In other cases, we recommend leaving them blank."
                            TextInput {
                                placeholder = "Min"
                                type = InputType.number
                                value = minValue
                                onChange = { e -> minValue = e.target.value }
                            }
                            TextInput {
                                placeholder = "Max"
                                type = InputType.number
                                value = maxValue
                                onChange = { e -> maxValue = e.target.value }
                            }
                        }
                        FormField {
                            title = "Unit"
                            comment = "Use short singular form (e.g. km, MWh)."
                            TextInput {
                                placeholder = "Enter the unit"
                                value = unit
                                onChange = { e -> unit = e.target.value }
                            }
                        }
                    }
                    QuestionType.DATE -> {
                        FormField {
                            title = "Range"
                            comment = "Set range only if the answers out of it do not make sense (e.g. a negative duration of an event). In other cases, we recommend leaving them blank."
                            TextInput {
                                placeholder = "Min"
                                type = InputType.date
                                value = minDateValue
                                onChange = { e -> minDateValue = e.target.value }
                            }
                            TextInput {
                                placeholder = "Max"
                                type = InputType.date
                                value = maxDateValue
                                onChange = { e -> maxDateValue = e.target.value }
                            }
                        }
                    }
                }
            }

            FormSection {
                title = "Resolution"
                FormField {
                    title = "Status"
                    OptionGroup<QuestionStatus>()() {
                        options = listOf(
                            QuestionStatus.OPEN to "Open",
                            QuestionStatus.CLOSED to "Closed",
                            QuestionStatus.RESOLVED to "Resolved"
                        )
                        defaultValue = QuestionStatus.OPEN
                        value = questionStatus
                        onChange = { status -> questionStatus = status }
                    }
                    comment = when (questionStatus) {
                        QuestionStatus.OPEN -> "The question is open to answers."
                        QuestionStatus.CLOSED -> "Room members cannot add new estimates or update them."
                        QuestionStatus.RESOLVED -> "Room members cannot update estimates and the resolution is shown."
                    }
                }
                FormField {
                    title = "Correct answer"
                    when (questionType) {
                        QuestionType.BINARY -> {
                            BinaryValueEntry {
                                value = binaryResolution
                                onChange = { value -> binaryResolution = value }
                            }
                        }
                        QuestionType.NUMERIC -> {
                            NumericValueEntry {
                                value = numericResolution
                                placeholder = "Enter the correct answer"
                                onChange = { value -> numericResolution = value }
                            }
                        }
                        QuestionType.DATE -> {
                            DateValueEntry {
                                value = dateResolution
                                placeholder = "Enter the correct answer"
                                onChange = { value -> dateResolution = value }
                            }
                        }
                    }
                    comment = "Will be shown to room members when the status is changed to Resolved."
                }
            }

            FormSection {
                title = "Visibility"
                FormSwitch {
                    label = "Question visible"
                    checked = isVisible
                    onChange = { e -> isVisible = e.target.checked }
                }
                FormSwitch {
                    label = "Allow comments"
                    checked = allowComments
                    onChange = { e -> allowComments = e.target.checked }
                }
                RadioGroup<GroupPredictionVisibility>()() {
                    title = "Group answer visible to"
                    options = listOf(
                        GroupPredictionVisibility.EVERYONE to "all room members",
                        // TODO: Backend support
                        //GroupPredictionVisibility.ANSWERED to "those who answered",
                        GroupPredictionVisibility.MODERATOR_ONLY to "moderators only",
                    )
                    value = groupPredictionVisibility
                    onChange = { visibility -> groupPredictionVisibility = visibility }
                }
                /*
                // TODO: Backend support
                FormSwitch {
                    label = "Sensitive"
                    comment = if (isSensitive) {
                        "Moderators will not be able to see individual answers."
                    } else {
                        "Moderators will be able to see individual answers."
                    }
                    checked = isSensitive
                    onChange = { e -> isSensitive = e.target.checked }
                }
                 */
            }

            Stack {
                css {
                    padding = Padding(20.px, 20.px, 10.px)
                }
                Button {
                    css {
                        fontWeight = integer(500)
                    }
                    +"Create question"
                    onClick = {
                        submitQuestion()
                    }
                }
            }
        }
    }
}

external interface NumericValueEntryProps : Props {
    var placeholder: String
    var value: Double?
    var onChange: ((Double?) -> Unit)?
}

external interface DateValueEntryProps : Props {
    var placeholder: String
    var value: LocalDate?
    var onChange: ((LocalDate?) -> Unit)?
    var min: Double
    var max: Double
}

private val NumericValueEntry = FC<NumericValueEntryProps> { props ->
    TextInput {
        type = InputType.number
        // TODO: Proper step
        //step = kotlin.math.min(0.1, props.space.binner.binSize)
        step = 0.1
        value = props.value ?: ""
        placeholder = props.placeholder
        onChange = { event ->
            val value = event.target.valueAsNumber
            if (!value.isNaN()) {
                props.onChange?.invoke(value)
            } else {
                props.onChange?.invoke(null)
            }
        }
    }
}

private val DateValueEntry = FC<DateValueEntryProps> { props ->
    TextInput {
        type = InputType.date
        // TODO: Check min and max work here
        //min = props.min
        //max = props.max
        value = props.value ?: ""
        placeholder = props.placeholder
        onChange = { event ->
            val date = try {
                LocalDate.parse(event.target.value)
            } catch (e: Exception) {
                null
            }
            props.onChange?.invoke(date)
        }
    }
}

external interface BinaryValueEntryProps : Props {
    var value: Boolean?
    var onChange: ((Boolean?) -> Unit)?
}

private val BinaryValueEntry = FC<BinaryValueEntryProps> { props ->
    // TODO: Styled Select instead of this
    val selectedValue = props.value

    RadioGroup<Boolean?>()() {
        options = listOf(
            null to "Not resolved",
            false to "No",
            true to "Yes"
        )
        value = selectedValue
        onChange = { value -> props.onChange?.invoke(value) }
    }
}
