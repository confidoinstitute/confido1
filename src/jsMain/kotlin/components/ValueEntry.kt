package components

import kotlinx.datetime.LocalDate
import kotlinx.js.Object
import mui.material.*
import react.*
import react.dom.html.InputType
import react.dom.onChange
import tools.confido.spaces.*
import tools.confido.utils.fromUnix
import utils.buildObject
import utils.eventNumberValue
import utils.eventValue
import utils.jsObject
import kotlin.math.min

external interface ValueEntryProps : Props {
    var space: Space
    var value: Value?
    var onChange: ((Value?) -> Unit)? // null = invalid value
    var label: String?
}

val ValueEntry = FC<ValueEntryProps> { props ->
    val space = props.space
    val value = props.value
    if (value != null && value.space != space) return@FC
    if (space is NumericSpace && (value is NumericValue?)) {
        val fc: FC<NumericValueEntryProps> = if (space.representsDays) DateValueEntry else NumericValueEntry
        fc {
            this.space = space
            this.value = value
            this.label = props.label
            this.onChange = props.onChange
        }
    }
    else if (space is BinarySpace && (value is BinaryValue?)) {
        BinaryValueEntry {
            this.value = value
            this.onChange = props.onChange
            this.label = props.label
        }
    }
}

external interface NumericValueEntryProps : Props {
    var space: NumericSpace
    var value: NumericValue?
    var onChange: ((Value?) -> Unit)?
    var label: String?
}
val NumericValueEntry = FC<NumericValueEntryProps> { props->
    TextField {
        type = InputType.number
        props.label?.let { this.label = ReactNode(it) }
        val step = min(0.1, props.space.binner.binSize) // TODO
        this.inputProps = jsObject {
            this.min = props.space.min
            this.max = props.space.max
            this.step = step
        }.unsafeCast<InputBaseComponentProps>()
        this.defaultValue = props.value?.value ?: ""
        this.asDynamic().InputProps = buildObject<InputProps> {
            endAdornment = InputAdornment.create {
                position = InputAdornmentPosition.end
                +props.space.unit
            }
        }
        onChange = { event->
            val nv = event.eventNumberValue()
            if (props.space.checkValue(nv))
                props.onChange?.invoke(NumericValue(props.space, nv))
            else
                props.onChange?.invoke(null)
        }
    }
}
val DateValueEntry = FC<NumericValueEntryProps> { props->
    TextField {
        type = InputType.date
        props.label?.let { this.label = ReactNode(it) }
        this.inputProps = jsObject {
            this.min = LocalDate.fromUnix(props.space.min).toString()
            this.max = LocalDate.fromUnix(props.space.max).toString()
        }.unsafeCast<InputBaseComponentProps>()
        this.defaultValue = props.value?.value?.let { LocalDate.fromUnix(it).toString() } ?: ""
        if (props.space.unit.isNotEmpty())
            this.asDynamic().InputProps = buildObject<InputProps> {
                endAdornment = InputAdornment.create {
                    position = InputAdornmentPosition.end
                    +props.space.unit
                }
            }
        onChange = { event->
            val date = try { LocalDate.parse(event.eventValue()) } catch  (e: Exception) { null }
            if (date != null) {
                val nv = date.toEpochDays() * 86400.0
                if (props.space.checkValue(nv))
                    props.onChange?.invoke(NumericValue(props.space, nv))
                else
                    props.onChange?.invoke(null)
            } else {
                props.onChange?.invoke(null)
            }
        }
    }
}
external interface BinaryValueEntryProps : Props {
    var value: BinaryValue?
    var onChange: ((Value?) -> Unit)?
    var label: String?
}
external interface BinaryValueSelectProps : Props {
    var value: BinaryValue?
    var onChange: ((Value?) -> Unit)?
    var labelId: String?
}

val BinaryValueEntry = FC<BinaryValueEntryProps> { props->
    val labelId = useId()
    val initialValue by useState(props.value)

    fun ChildrenBuilder.makeSelect() {
        val select: FC<SelectProps<String>> = Select
        select {
            this.defaultValue = when (initialValue?.value) {
                true -> "true"
                false -> "false"
                else -> ""
            }
            props.label?.let {
                this.labelId = labelId
                this.label = ReactNode(it)
            }
            MenuItem {
                this.value = "false"
                +"No"
            }
            MenuItem {
                this.value = "true"
                +"Yes"
            }
            onChange = { event, _ ->
                val newVal = when (event.target.value) {
                    "true" -> BinaryValue(true)
                    "false" -> BinaryValue(false)
                    else -> null
                }
                props.onChange?.invoke(newVal)
            }
        }
    }
    if (props.label != null) {
        FormControl {
            this.fullWidth = true
            InputLabel {
                this.id = labelId
                //this.error =
                +(props.label ?: "")
            }
            makeSelect()
        }
    } else {
        makeSelect()
    }

}

