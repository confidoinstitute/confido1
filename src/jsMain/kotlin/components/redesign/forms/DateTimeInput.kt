package components.redesign.forms

import components.redesign.basic.Stack
import csstype.FlexDirection
import csstype.pct
import csstype.px
import emotion.react.css
import hooks.useEffectNotFirst
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import react.*
import react.dom.html.InputType
import tools.confido.utils.multiletNotNull

external interface DateInputProps : PropsWithClassName, InputPropsWithRange<LocalDate> {
    var id: String?
}

val DateInput = FC<DateInputProps> { props ->
    var focused by useState(false)
    var rawValue by useState(props.value?.toString() ?: "")
    val ism = useInputStateManager(props) {
        rawValue = it?.toString() ?: ""
    }
    fun onRawChange(newRaw: String) {
        if (newRaw.isEmpty()) {
            ism.update(null, null)
        } else {
            val date = try {
                LocalDate.parse(newRaw)
            } catch (e: Exception) {
                return ism.update(null, InvalidFormat)
            }
            if (props.min?.let { date < it } == true) {
                ism.update(null, InputTooSmall(props.min!!))
            } else if (props.max?.let { date > it } == true) {
                ism.update(null, InputTooLarge(props.max!!))
            } else {
                ism.update(date, null)
            }
        }
    }
    useEffectNotFirst(props.min.toString(), props.max.toString()) {
        // reevaluate error if constraints change
        onRawChange(rawValue)
    }
    TextInput {
        this.className = props.className
        type = if (rawValue == "" && !props.placeholder.isNullOrEmpty() && !focused) InputType.text else InputType.date

        placeholder = props.placeholder
        onFocus = { focused = true }
        onBlur = { focused = false }
        size = 10
        props.min?.let { min = it.toString() }
        props.max?.let { max = it.toString() }
        props.disabled?.let { this.disabled = it }
        props.readOnly?.let { this.readOnly = it }
        this.value = rawValue
        required = props.required
        id = props.id
        onChange = { event ->
            val newValue = event.target.value
            rawValue = newValue
            onRawChange(newValue)
        }
    }
}

external interface TimeInputProps : PropsWithClassName, InputPropsWithRange<LocalTime> {
}

val TimeInput = FC<TimeInputProps> { props ->
    var rawValue by useState(props.value?.toString() ?: "")
    val ism = useInputStateManager(props) { rawValue = it?.toString() ?: "" }

    fun onRawChange(newValue: String) {
        if (newValue.isEmpty()) {
            ism.update(null, null)
        } else {
            val time = try {
                LocalTime.parse(newValue)
            } catch (e: Exception) {
                return ism.update(null, InvalidFormat)
            }
            if (props.min?.let { time < it } == true) {
                ism.update(null, InputTooSmall(props.min!!))
            } else if (props.max?.let { time > it } == true) {
                ism.update(null, InputTooLarge(props.max!!))
            } else {
                ism.update(time, null)
            }
        }
    }
    useEffectNotFirst(props.min.toString(), props.max.toString()) {
        // reevaluate error if constraints change
        onRawChange(rawValue)
    }

    TextInput {
        this.className = props.className
        type = InputType.time
        props.min?.let { min = it.toString() }
        props.max?.let { max = it.toString() }
        this.value = rawValue
        required = props.required
        props.disabled?.let { this.disabled = it }
        props.readOnly?.let { this.readOnly = it }
        onChange = { event ->
            val newValue = event.target.value
            rawValue = newValue
            onRawChange(newValue)
        }
    }
}

external interface DateTimeInputProps : InputPropsWithRange<LocalDateTime> {
    var defaultTime: LocalTime?
    var wrap: ((ReactNode, ReactNode) -> ReactNode)?
    var dateProps: DateInputProps?
    var timeProps: TimeInputProps?
}

val DateTimeInput = FC<DateTimeInputProps> { props->
    var date by useState(props.value?.date)
    var time by useState(props.value?.time)
    val ism = useInputStateManager(props) { date = it?.date; time = it?.time; }
    var dateErr by useState<InputError>()
    var timeErr by useState<InputError>()
    val wrap = props.wrap ?: if (props.inFormField == true) { di,ti -> Fragment.create {+di;+ti} }
            else { di,ti -> Stack.create { direction  = FlexDirection.row; css { gap = 7.px; }; +di; +ti } }
    fun update(newDate: LocalDate?, newDateErr: InputError?, newTime: LocalTime?, newTimeErr: InputError?) {
        val newTimeEff = newTime ?: props.defaultTime
        val newDT = if (newDateErr == null && newTimeErr == null)
            multiletNotNull(newDate, newTimeEff)  { d,t -> LocalDateTime(d,t) }
        else null
        val newErr = newDateErr ?: newTimeErr
        ism.update(newDT, newErr)
    }
    useEffectNotFirst(props.min.toString(), props.max.toString()) {
        // reevaluate error if constraints change
        update(date,dateErr,time,timeErr)
    }
    +wrap(
        DateInput.create {
            this.value = date
            this.placeholder = props.placeholder
            onChange = { newDate, err ->
                date = newDate
                dateErr = err
                val newTime = if (newDate != null && time == null && props.defaultTime != null) {
                    time = props.defaultTime
                    props.defaultTime
                } else time
                update(newDate, err, newTime, timeErr)
            }
            css {
                flexBasis = 50.pct
            }
            +props.dateProps
        },
        TimeInput.create {
            this.value = time
            this.disabled = (props.disabled == true || date == null)
            this.readOnly = props.readOnly
            onChange = { newTime, err->
                time = newTime
                timeErr = err
                update(date, dateErr, newTime, err)
            }
            css {
                flexBasis = 50.pct
            }
            +props.timeProps
        }
    )
}
