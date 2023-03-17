package components.redesign.questions.dialog

import components.redesign.forms.FormField
import components.redesign.forms.FormSection
import components.redesign.forms.OptionGroup
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import react.FC
import react.Props
import react.useEffect
import react.useState
import tools.confido.spaces.*
import tools.confido.utils.fromUnix

internal external interface EditQuestionDialogResolutionProps : Props {
    var preset: QuestionPreset
    
    var status: QuestionStatus
    var onStatusChange: (QuestionStatus) -> Unit

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

    // TODO properly
    if (props.preset != QuestionPreset.SENSITIVE && props.preset != QuestionPreset.BELIEF)
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