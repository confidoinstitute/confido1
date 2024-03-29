package components.redesign.questions.dialog

import components.redesign.forms.*
import kotlinx.datetime.*
import react.*
import react.dom.html.*
import tools.confido.spaces.*
import tools.confido.utils.*

internal enum class AnswerSpaceError {
    INVALID,
    BAD_RANGE,
}

internal external interface EditQuestionDialogSpaceProps : Props {
    var space: Space?
    var onChange: (Space) -> Unit
    var onError: () -> Unit
    var readOnly: Boolean?
}

internal val EditQuestionDialogSpace = FC<EditQuestionDialogSpaceProps> { props ->
    var minValue by useState("")
    var maxValue by useState("")
    var minDateValue by useState("")
    var maxDateValue by useState("")
    var unit by useState("")

    var error: AnswerSpaceError? by useState(null)

    val readOnly = props.readOnly ?: false
    val outSpace = props.space
    var questionType by useState(outSpace?.questionType ?: QuestionType.BINARY)

    useEffect(outSpace) {
        console.log("Space part update")
        if (outSpace == null) return@useEffect
        questionType = outSpace.questionType
        when (outSpace.questionType) {
            QuestionType.BINARY -> {}
            QuestionType.NUMERIC -> {
                val nSpace = outSpace as NumericSpace
                minValue = nSpace.min.toString()
                maxValue = nSpace.max.toString()
                unit = nSpace.unit
            }
            QuestionType.DATE -> {
                val dSpace = outSpace as NumericSpace
                minDateValue = LocalDate.fromUnix(dSpace.min).toString()
                maxDateValue = LocalDate.fromUnix(dSpace.max).toString()
            }
        }
    }

    useEffect(questionType, minValue, maxValue, minDateValue, maxDateValue, unit) {
        console.log("Space input update")
        error = null
        when (questionType) {
            QuestionType.BINARY -> props.onChange(BinarySpace)
            QuestionType.NUMERIC -> {
                try {
                    val min = minValue.toDouble()
                    val max = maxValue.toDouble()
                    if (min >= max) {
                        error = AnswerSpaceError.BAD_RANGE
                        props.onError()
                    } else {
                        val space = NumericSpace(min, max, unit = unit)
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
        when (error) {
            AnswerSpaceError.INVALID -> this.error = "The given range is invalid."
            AnswerSpaceError.BAD_RANGE -> {
                if (questionType == QuestionType.DATE) {
                    this.error = "The range interval must be non-empty. Make sure the second date is later than the first one."
                } else {
                    this.error = "The range interval must be non-empty. Make sure the second value is larger than the first one."
                }
            }
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
                disabled = readOnly
            }
            comment = when (questionType) {
                QuestionType.BINARY -> "The question asks whether something is (will be) true."
                QuestionType.NUMERIC -> "The answer to this question is a number."
                QuestionType.DATE -> "The answer to this question is a date."
            }
        }
        when (questionType) {
            QuestionType.BINARY -> {}
            QuestionType.NUMERIC -> {
                FormField {
                    title = "Range"
                    comment =
                        "We recommend setting a range wide enough to cover unlikely/unexpected but possible outcomes. The range cannot be changed later."
                        //"Set range only if the answers out of it do not make sense (e.g. a negative duration of an event). In other cases, we recommend leaving them blank."
                    TextInput {
                        placeholder = "Min"
                        type = InputType.number
                        value = minValue
                        onChange = { e -> minValue = e.target.value }
                        disabled = readOnly
                    }
                    TextInput {
                        placeholder = "Max"
                        type = InputType.number
                        value = maxValue
                        onChange = { e -> maxValue = e.target.value }
                        disabled = readOnly
                    }
                    answerSpaceError()
                }
                FormField {
                    title = "Unit"
                    comment = "Use short singular form (e.g. km, MWh)."
                    TextInput {
                        placeholder = "Enter the unit"
                        value = unit
                        onChange = { e -> unit = e.target.value }
                        disabled = readOnly
                    }
                }
            }
            QuestionType.DATE -> {
                FormField {
                    title = "Range"
                    comment =
                        "We recommend setting a range wide enough to cover unlikely/unexpected but possible outcomes. The range cannot be changed later."
                        //"Set range only if the answers out of it do not make sense (e.g. a negative duration of an event). In other cases, we recommend leaving them blank."
                    TextInput {
                        placeholder = "Min"
                        type = InputType.date
                        value = minDateValue
                        onChange = { e -> minDateValue = e.target.value }
                        disabled = readOnly
                    }
                    TextInput {
                        placeholder = "Max"
                        type = InputType.date
                        value = maxDateValue
                        onChange = { e -> maxDateValue = e.target.value }
                        disabled = readOnly
                    }
                    answerSpaceError()
                }
            }
        }
    }

}
