package components.redesign.questions.dialog

import components.redesign.forms.*
import kotlinx.datetime.*
import react.*
import react.dom.html.*
import tools.confido.utils.toFixed

external interface NumericValueEntryProps : Props {
    var placeholder: String
    var required: Boolean?
    var value: Double?
    var onChange: ((Double?) -> Unit)?
}


internal val NumericValueEntry = FC<NumericValueEntryProps> { props ->
    var value by useState(props.value?.toString() ?: "");

    TextInput {
        type = InputType.number
        // TODO: Proper step
        //step = kotlin.math.min(0.1, props.space.binner.binSize)
        step = 0.1
        this.value = value
        placeholder = props.placeholder
        required = props.required
        onChange = { event ->
            value = event.target.value
            val newValue = event.target.valueAsNumber
            if (!newValue.isNaN()) {
                props.onChange?.invoke(newValue)
            } else {
                props.onChange?.invoke(null)
            }
        }
    }
}

external interface BinaryValueEntryProps : Props {
    var value: Boolean?
    var required: Boolean?
    var onChange: ((Boolean?) -> Unit)?
}

internal val BinaryValueEntry = FC<BinaryValueEntryProps> { props ->
    // TODO: Styled Select instead of this
    val selectedValue = props.value
    val required = props.required ?: false

    RadioGroup<Boolean?>()() {
        options = if (required) {
            listOf(
                false to "No",
                true to "Yes"
            )
        } else {
            listOf(
                null to "Not resolved",
                false to "No",
                true to "Yes"
            )
        }
        value = selectedValue
        onChange = { value -> props.onChange?.invoke(value) }
    }
}