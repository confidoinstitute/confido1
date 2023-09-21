package components.redesign.forms

import components.redesign.basic.Stack
import csstype.FlexDirection
import csstype.pct
import emotion.react.css
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import react.*
import react.dom.html.InputType
import tools.confido.utils.multiletNotNull

external interface DateInputProps : PropsWithClassName {
    var required: Boolean?
    var value: LocalDate?
    var onChange: ((LocalDate?) -> Unit)?
    var onError: (() -> Unit)?
    var min: LocalDate?
    var max: LocalDate?
    var id: String?
    var placeholder: String?
    var disabled: Boolean?
    var readOnly: Boolean?
}

internal val DateInput = FC<DateInputProps> { props ->
    var focused by useState(false)
    TextInput {
        this.className = props.className
        type = if (props.value == null && props.placeholder != null && !focused) InputType.text else InputType.date
        placeholder = props.placeholder
        disabled = props.disabled ?: false
        readOnly = props.readOnly ?: false
        onFocus = { focused = true }
        onBlur = { focused = false }
        size = 10
        if (props.min != null) min = props.min
        if (props.max != null) max = props.max
        value = props.value ?: ""
        required = props.required
        id = props.id
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

external interface TimeInputProps : PropsWithClassName {
    var required: Boolean?
    var value: LocalTime?
    var onChange: ((LocalTime?) -> Unit)?
    var onError: (() -> Unit)?
    var min: LocalTime?
    var max: LocalTime?
    var disabled: Boolean?
    var readOnly: Boolean?
    // NOTE placeholders are not supported for date/time inputs by HTML5
}

val TimeInput = FC<TimeInputProps> { props ->
    TextInput {
        this.className = props.className
        type = InputType.time
        if (props.min != null) min = props.min
        if (props.max != null) max = props.max
        value = props.value ?: ""
        required = props.required
        disabled = props.disabled ?: false
        readOnly = props.readOnly ?: false
        onChange = { event ->
            val time = try {
                val value = event.target.value
                if (value.isEmpty())
                    null
                else
                    LocalTime.parse(event.target.value)
            } catch (e: Exception) {
                props.onError?.invoke()
                null
            }
            props.onChange?.invoke(time)
        }
    }
}

external interface DateTimeInputProps : Props {
    var required: Boolean?
    var value: LocalDateTime?
    var onChange: ((LocalDateTime?) -> Unit)?
    //var onError: (() -> Unit)?
    var min: LocalDateTime?
    var max: LocalDateTime?
    var defaultTime: LocalTime?
    var wrap: ((ReactNode, ReactNode) -> ReactNode)?
    var dateProps: DateInputProps?
    var timeProps: TimeInputProps?
    var placeholder: String?
    var disabled: Boolean?
    var readOnly: Boolean?
}

val DateTimeInput = FC<DateTimeInputProps> { props->
    val value = props.value
    var date by useState(value?.date)
    var time by useState(value?.time)
    val wrap = props.wrap ?: { di,ti ->
        Fragment.create { +di; +ti; }
    }
    useEffect(props.value.toString()) {
        println("EFF ${props.value}")
        date = props.value?.date
        time = props.value?.time
    }
    fun update(newDate: LocalDate?, newTime: LocalTime?) {
        val newTimeEff = newTime ?: props.defaultTime
        val newDT = multiletNotNull(newDate, newTimeEff)  { d,t -> LocalDateTime(d,t) }
        if (newDT != props.value)
            props.onChange?.invoke(newDT)
    }
    +wrap(
        DateInput.create {
            this.value = date
            this.placeholder = props.placeholder
            this.disabled = props.disabled == true
            this.readOnly = props.readOnly
            onChange = {newDate ->
                date = newDate
                val newTime = if (newDate != null && time == null && props.defaultTime != null) {
                    time = props.defaultTime
                    props.defaultTime
                } else time
                update(newDate, newTime)
            }
            +props.dateProps
        },
        TimeInput.create {
            this.value = time
            this.disabled = (props.disabled == true || date == null)
            this.readOnly = props.readOnly
            onChange = {
                time = it
                update(date, it)
            }
            +props.timeProps
        }
    )
}
