package components.redesign.questions.dialog

import components.redesign.forms.*
import kotlinx.datetime.*
import react.*
import react.dom.html.*
import tools.confido.spaces.*
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow


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

external interface SpaceValueEntryProps: InputProps<Value> {
    var space: Space
}

val SpaceValueEntry = FC<SpaceValueEntryProps> { props->
    val space = props.space
    val valueRequired = props.required ?: false
    when (space) {
        is BinarySpace -> {
            BinaryValueEntry {
                value = (props.value as? BinaryValue)?.value
                required = valueRequired
                onChange = { props.onChange?.invoke(it?.let { BinaryValue(it) }, null) }
            }
        }

        is NumericSpace-> {
            if (space.representsDays) {

                fun ts2ld(ts: Double) = Instant.fromEpochSeconds(ts.toLong()).toLocalDateTime(
                    TimeZone.UTC
                ).date
                fun ts2ld(ts: Double?) = ts?.let { ts2ld(ts) }
                fun ld2ts(ld: LocalDate) = ld.atStartOfDayIn(TimeZone.UTC).epochSeconds.toDouble()
                fun ld2ts(ld: LocalDate?) = ld?.let{ ld2ts(ld) }
                fun val2ld(v: Value) = ts2ld((v as NumericValue).value)
                fun ld2val(ld: LocalDate) = NumericValue(space, ld2ts(ld))
                fun ld2val(ld: LocalDate?) = ld?.let { ld2val(ld) }
                fun val2ld(v: Value?) = ts2ld((v as? NumericValue)?.value)
                DateInput {
                    value = val2ld(props.value)
                    placeholder = props.placeholder
                    required = valueRequired
                    min = ts2ld(space.min)
                    max = ts2ld(space.max)
                    onChange = { v, err->
                        props.onChange?.invoke(ld2val(v), err)
                    }
                }
            } else {
                NumericInput {
                    value = (props.value as? NumericValue)?.value
                    placeholder = props.placeholder ?: ""
                    required = valueRequired
                    min = space.min
                    max = space.max
                    step = 10.0.pow(minOf(ceil(log10(space.binner.binSize)) - 1, -1.0))
                    onChange = { v, err ->
                        props.onChange?.invoke(v?.let { NumericValue(space, v) }, err)
                    }
                }
            }
        }
    }
}