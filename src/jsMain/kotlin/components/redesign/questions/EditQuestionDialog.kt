package components.redesign.questions

import components.AppStateContext
import components.redesign.basic.Dialog
import components.redesign.basic.Stack
import components.redesign.forms.*
import components.redesign.forms.Button
import components.rooms.RoomContext
import components.showError
import csstype.*
import emotion.react.css
import hooks.EditEntityDialogProps
import hooks.useCoroutineLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import payloads.requests.EditQuestion
import payloads.requests.EditQuestionComplete
import react.*
import react.dom.html.InputType
import tools.confido.question.Question
import tools.confido.spaces.*
import tools.confido.utils.fromUnix

external interface EditQuestionDialogProps : EditEntityDialogProps<Question> {
}

private enum class QuestionType {
    BINARY,
    NUMERIC,
    DATE,
}

private val Space.questionType: QuestionType get() =
    when(this) {
        BinarySpace -> QuestionType.BINARY
        is NumericSpace -> if (representsDays) QuestionType.DATE else QuestionType.NUMERIC
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

private enum class AnswerSpaceError {
    INVALID,
    BAD_RANGE,
}

private external interface EditQuestionDialogSpaceProps : Props {
    var space: Space?
    var onChange: (Space) -> Unit
    var onError: () -> Unit

    var unit: String
    var onUnitChange: (String) -> Unit
}

private val EditQuestionDialogSpace = FC<EditQuestionDialogSpaceProps> {props ->
    var minValue by useState("")
    var maxValue by useState("")
    var minDateValue by useState("")
    var maxDateValue by useState("")

    var error: AnswerSpaceError? by useState(null)

    val outSpace = props.space
    var questionType by useState(outSpace?.questionType ?: QuestionType.BINARY)

    useEffect(outSpace) {
        console.log("Space part update")
        if (outSpace == null) return@useEffect
        questionType = outSpace.questionType
        when(outSpace.questionType) {
            QuestionType.BINARY -> {}
            QuestionType.NUMERIC -> {
                val nSpace = outSpace as NumericSpace
                minValue = nSpace.min.toString()
                maxValue = nSpace.max.toString()
            }
            QuestionType.DATE -> {
                val dSpace = outSpace as NumericSpace
                minDateValue = LocalDate.fromUnix(dSpace.min).toString()
                maxDateValue = LocalDate.fromUnix(dSpace.max).toString()
            }
        }
    }

    useEffect(questionType, minValue, maxValue, minDateValue, maxDateValue) {
        console.log("Space input update")
        error = null
        when(questionType) {
            QuestionType.BINARY -> props.onChange(BinarySpace)
            QuestionType.NUMERIC -> {
                try {
                    val min = minValue.toDouble()
                    val max = maxValue.toDouble()
                    if (min >= max) {
                        error = AnswerSpaceError.BAD_RANGE
                        props.onError()
                    } else {
                        val space = NumericSpace(min, max)
                        props.onChange(space)
                    }
                } catch (e: NumberFormatException) {
                    error = AnswerSpaceError.INVALID
                    props.onError()
                }
            }
            QuestionType.DATE -> {
                try {
                    val min = LocalDate.parse(minDateValue)
                    val max = LocalDate.parse(maxDateValue)
                    if (min >= max) {
                        error = AnswerSpaceError.BAD_RANGE
                        props.onError()
                    } else {
                        val space = NumericSpace.fromDates(min, max)
                        props.onChange(space)
                    }
                } catch (e: IllegalArgumentException) {
                    error = AnswerSpaceError.INVALID
                    props.onError()
                }
            }
        }
    }

    fun FormFieldProps.answerSpaceError() {
        when(error) {
            AnswerSpaceError.INVALID -> this.error = "The given range is invalid."
            AnswerSpaceError.BAD_RANGE -> this.error = "The range must cover non-empty interval."
            null -> {}
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
                    answerSpaceError()
                }
                FormField {
                    title = "Unit"
                    comment = "Use short singular form (e.g. km, MWh)."
                    TextInput {
                        placeholder = "Enter the unit"
                        value = props.unit
                        onChange = { e -> props.onUnitChange(e.target.value) }
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
                    answerSpaceError()
                }
            }
        }
    }

}

private external interface EditQuestionDialogResolutionProps : Props {
    var status: QuestionStatus
    var onStatusChange: (QuestionStatus) -> Unit

    var space: Space?
    var value: Value?
    var valid: Boolean
    var onChange: (Value?) -> Unit
    var onError: () -> Unit
}

private val EditQuestionDialogResolution = FC<EditQuestionDialogResolutionProps> {props ->
    var binaryResolution by useState<Boolean?>(null)
    var dateResolution by useState<LocalDate?>(null)
    var dateError by useState(false)
    var numericResolution by useState<Double?>(null)

    val questionType = props.space?.questionType

    useEffect(props.space, binaryResolution, dateResolution.toString(), numericResolution) {
        console.log("Resolution part update")
        try {
            val value: Value? = when (questionType) {
                QuestionType.BINARY -> binaryResolution?.let { BinaryValue(it) }
                QuestionType.NUMERIC -> numericResolution?.let { NumericValue(props.space as NumericSpace, it) }
                QuestionType.DATE -> dateResolution?.let {
                    val timestamp = it.atStartOfDayIn(TimeZone.UTC).epochSeconds
                    NumericValue(props.space as NumericSpace, timestamp.toDouble())
                }
                null -> null
            }
            props.onChange(value)
        } catch (e: IllegalArgumentException) {
            props.onError()
        }
    }

    useEffect(props.value) {
        if (questionType == null) {
            binaryResolution = null
            numericResolution = null
            dateResolution = null
        }
        when(val value = props.value) {
            is BinaryValue -> binaryResolution = value.value
            is NumericValue ->
                if (questionType == QuestionType.NUMERIC)
                    numericResolution = value.value
                else
                    dateResolution = LocalDate.fromUnix(value.value)
            else -> {}
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
                value = props.status
                onChange = { props.onStatusChange(it) }
            }
            comment = when (props.status) {
                QuestionStatus.OPEN -> "The question is open to answers."
                QuestionStatus.CLOSED -> "Room members cannot add new estimates or update them."
                QuestionStatus.RESOLVED -> "Room members cannot update estimates and the resolution is shown."
            }
        }
        FormField {
            title = "Correct answer"
            if (props.space != null)
            when (questionType) {
                QuestionType.BINARY -> {
                    BinaryValueEntry {
                        value = binaryResolution
                        onChange = { binaryResolution = it }
                    }
                }
                QuestionType.NUMERIC -> {
                    NumericValueEntry {
                        value = numericResolution
                        placeholder = "Enter the correct answer"
                        onChange = { numericResolution = it }
                    }
                }
                QuestionType.DATE -> {
                    DateValueEntry {
                        value = dateResolution
                        placeholder = "Enter the correct answer"
                        onChange = { dateResolution = it; dateError = false }
                        onError = { dateError = true }
                    }
                }
                else -> {}
            }
            if (questionType == null)
                error = "The answer space is not well defined, please correct it."
            else if (dateError)
                error = "This is not a valid date."
            else if (!props.valid)
                error = "This resolution is not within bounds of the answer space."
            if (props.value != null) {
                if (props.status != QuestionStatus.RESOLVED)
                    comment = "Will be shown to room members when the status is changed to Resolved."
                else
                    comment = "Will be shown to room members."
            } else
                comment = "This question has no correct answer, thus nothing will be shown."

        }
    }

}

val EditQuestionDialog = FC<EditQuestionDialogProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    var questionType by useState(props.entity?.answerSpace?.questionType ?: QuestionType.BINARY)

    var questionStatus by useState {
        props.entity?.let {
            when {
                it.resolved && it.resolutionVisible -> QuestionStatus.RESOLVED
                it.open -> QuestionStatus.OPEN
                else -> QuestionStatus.CLOSED
            }
        } ?: QuestionStatus.OPEN
    }
    var questionTitle by useState(props.entity?.name ?: "")
    var questionDescription by useState(props.entity?.description ?: "")

    var answerSpace: Space? by useState(props.entity?.answerSpace ?: BinarySpace)
    var answerSpaceValid: Boolean by useState(false)
    var unit by useState("")

    var resolution: Value? by useState(props.entity?.resolution)
    var resolutionValid: Boolean by useState(false)

    val questionValid = questionTitle.isNotEmpty() && answerSpaceValid && resolutionValid

    var groupPredictionVisibility by useState(if (props.entity?.groupPredVisible != true) GroupPredictionVisibility.MODERATOR_ONLY else GroupPredictionVisibility.EVERYONE)
    var isVisible by useState(props.entity?.visible ?: true)
    var allowComments by useState(true)
    var isSensitive by useState(false)

    val submit = useCoroutineLock()
    fun submitQuestion() = submit {
        if (!questionValid) return@submit
        val question = tools.confido.question.Question(
            id = props.entity?.id ?: "",
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

        if (props.entity == null) {
            Client.sendData("${room.urlPrefix}/questions/add", question, onError = {showError?.invoke(it)}) {props.onClose?.invoke()}
        } else {
            val editQuestion: EditQuestion = EditQuestionComplete(question)
            Client.sendData("${props.entity?.urlPrefix}/edit", editQuestion, onError = {showError?.invoke(it)}) {props.onClose?.invoke()}
        }
    }

    Dialog {
        open = props.open
        onClose = props.onClose
        title = if (props.entity != null) "Edit this question" else "Create a question"
        action = if (props.entity != null) "Edit" else "Create"
        disabledAction = (stale || !questionValid)
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
                        required = true
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

            EditQuestionDialogSpace {
                space = answerSpace
                onChange = { answerSpace = it; answerSpaceValid = true }
                onError = {
                    answerSpace = null
                    answerSpaceValid = false
                }
                this.unit = unit
                onUnitChange = {unit = it}
            }

            EditQuestionDialogResolution {
                status = questionStatus
                onStatusChange = {questionStatus = it}

                space = answerSpace
                value = resolution
                valid = resolutionValid
                this.onChange = {
                    resolution = it
                    resolutionValid = true
                }
                this.onError = {
                    resolution = null
                    resolutionValid = false
                }
            }

            FormSection {
                title = "Visibility"
                FormSwitch {
                    label = "Question visible"
                    checked = isVisible
                    onChange = { e -> isVisible = e.target.checked }
                }
                /*
                FormSwitch {
                    label = "Allow comments"
                    checked = allowComments
                    onChange = { e -> allowComments = e.target.checked }
                }
                 */
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
                    if (props.entity != null)
                        +"Edit question"
                    else
                        +"Create question"
                    disabled = (stale || !questionValid)
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
    var onError: (() -> Unit)?
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
        min = props.min
        max = props.max
        value = props.value ?: ""
        placeholder = props.placeholder
        onChange = { event ->
            val date = try {
                val value = event.target.value
                if (value.isEmpty())
                    null
                else
                    LocalDate.parse(event.target.value)
            } catch (e: Exception) {
                props.onError?.invoke()
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
