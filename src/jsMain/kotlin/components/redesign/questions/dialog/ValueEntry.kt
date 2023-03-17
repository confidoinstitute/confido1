package components.redesign.questions.dialog

import components.redesign.forms.RadioGroup
import components.redesign.forms.TextInput
import kotlinx.datetime.LocalDate
import react.FC
import react.Props
import react.dom.html.InputType

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

internal val NumericValueEntry = FC<NumericValueEntryProps> { props ->
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
internal val DateValueEntry = FC<DateValueEntryProps> { props ->
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

internal val BinaryValueEntry = FC<BinaryValueEntryProps> { props ->
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