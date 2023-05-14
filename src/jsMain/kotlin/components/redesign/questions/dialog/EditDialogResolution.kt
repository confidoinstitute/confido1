package components.redesign.questions.dialog

import components.redesign.forms.*
import kotlinx.datetime.*
import react.*
import tools.confido.question.QuestionState
import tools.confido.spaces.*
import tools.confido.utils.*

internal external interface EditQuestionDialogResolutionProps : Props {
    var preset: QuestionPreset?
    var state: QuestionState
    /** Optional. In case this is null, the status section will not be shown. */
    var onStateChange: ((QuestionState) -> Unit)?
    var valueRequired: Boolean?
    var space: Space?
    var value: Value?
    var valid: Boolean
    var onChange: (Value?) -> Unit
    var onError: () -> Unit
}

internal val EditQuestionDialogResolution = FC<EditQuestionDialogResolutionProps> { props ->
    var binaryResolution by useState<Boolean?>(null)
    var dateResolution by useState<LocalDate?>(null)
    var dateError by useState(false)
    var numericResolution by useState<Double?>(null)

    val questionType = props.space?.questionType
    val valueRequired = props.valueRequired ?: false

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
            return@useEffect
        }
        when (val value = props.value) {
            is BinaryValue -> binaryResolution = value.value
            is NumericValue ->
                if (questionType == QuestionType.NUMERIC)
                    numericResolution = value.value
                else
                    dateResolution = LocalDate.fromUnix(value.value)
            else -> {}
        }
    }

    if (props.onStateChange != null) {
        FormField {
            title = "Status"
            OptionGroup<QuestionState>()() {
                options = listOf(
                    QuestionState.OPEN to "Open",
                    QuestionState.CLOSED to "Closed",
                    QuestionState.RESOLVED to "Resolved"
                )
                defaultValue = QuestionState.OPEN
                value = props.state
                onChange = { props.onStateChange?.invoke(it) }
            }
            comment = when (props.state) {
                QuestionState.OPEN -> "The question is open to answers."
                QuestionState.CLOSED -> "Room members cannot add new estimates or update them."
                QuestionState.RESOLVED -> "Room members cannot update estimates and the resolution is shown."
                QuestionState.ANNULLED -> "Room members cannot update estimates or update them."
            }
        }
    }
    FormField {
        title = "Correct answer"
        required = valueRequired
        if (props.space != null)
            when (questionType) {
                QuestionType.BINARY -> {
                    BinaryValueEntry {
                        value = binaryResolution
                        required = valueRequired
                        onChange = { binaryResolution = it }
                    }
                }
                QuestionType.NUMERIC -> {
                    NumericValueEntry {
                        value = numericResolution
                        placeholder = "Enter the correct answer"
                        required = valueRequired
                        onChange = { numericResolution = it }
                    }
                }
                QuestionType.DATE -> {
                    DateValueEntry {
                        value = dateResolution
                        placeholder = "Enter the correct answer"
                        required = valueRequired
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
            error = "This resolution is not within the answer range."
        if (props.value != null) {
            if (props.state != QuestionState.RESOLVED)
                comment = "Will be shown to room members when the status is changed to Resolved."
            else
                comment = "Will be shown to room members."
        } else if (!valueRequired) {
            comment = "This question has no correct answer, thus nothing will be shown."
        }
    }
}