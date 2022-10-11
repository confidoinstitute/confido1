package components

import kotlinx.datetime.LocalDate
import mui.material.*
import org.w3c.dom.HTMLFormElement
import org.w3c.xhr.FormData
import react.*
import react.dom.html.ButtonType
import react.dom.html.InputType
import react.dom.html.ReactHTML
import tools.confido.utils.fromUnix
import utils.buildObject
import utils.jsObject

external interface PreciseInputFormProps : Props {
    var value: dynamic
    var min: dynamic
    var max: dynamic
    var step: dynamic
    var unit: String
    var disabled: Boolean?
    var onCancel: (() -> Unit)?
    var onSubmit: ((Double) -> Unit)?
}

val PreciseInputNumber = FC<PreciseInputFormProps> {props ->

    ReactHTML.form {
        onSubmit = {
            it.preventDefault()
            it.stopPropagation()
            val target = it.target as HTMLFormElement
            val number = (FormData(target).get("preciseInput") as String).toDouble()
            props.onSubmit?.invoke(number)
        }
        DialogContent {
            TextField {
                name = "preciseInput"
                type = InputType.number
                this.inputProps = jsObject {
                    this.min = props.min
                    this.max = props.max
                    this.step = props.step
                }.unsafeCast<InputBaseComponentProps>()
                this.defaultValue = props.value
                autoFocus = true
                this.asDynamic().InputProps = buildObject<InputProps> {
                    endAdornment = InputAdornment.create {
                        position = InputAdornmentPosition.end
                        +props.unit
                    }
                }}
        }
        DialogActions {
            Button {
                onClick = { props.onCancel?.invoke() }
                +"Cancel"
            }
            Button {
                type = ButtonType.submit
                disabled = props.disabled
                +"Set"
            }
        }
    }
}

val PreciseInputDate = FC<PreciseInputFormProps> {props ->

    ReactHTML.form {
        onSubmit = {
            it.preventDefault()
            it.stopPropagation()
            val target = it.target as HTMLFormElement
            val value = FormData(target).get("preciseInput") as String
            val date = try { LocalDate.parse(value) } catch  (e: Exception) { null }
            date?.let {
                props.onSubmit?.invoke(it.toEpochDays() * 86400.0)
            }
        }
        DialogContent {
            TextField {
                name = "preciseInput"
                type = InputType.date
                this.inputProps = jsObject {
                    this.min = LocalDate.fromUnix(props.min).toString()
                    this.max = LocalDate.fromUnix(props.max).toString()
                }.unsafeCast<InputBaseComponentProps>()
                this.defaultValue = LocalDate.fromUnix(props.value).toString()
                autoFocus = true
                if (props.unit.isNotEmpty())
                this.asDynamic().InputProps = buildObject<InputProps> {
                    endAdornment = InputAdornment.create {
                        position = InputAdornmentPosition.end
                        +props.unit
                    }
                }}
        }
        DialogActions {
            Button {
                onClick = { props.onCancel?.invoke() }
                +"Cancel"
            }
            Button {
                type = ButtonType.submit
                disabled = props.disabled
                +"Set"
            }
        }
    }
}

val PreciseInputPercent = FC<PreciseInputFormProps> {props ->

    ReactHTML.form {
        onSubmit = {
            it.preventDefault()
            it.stopPropagation()
            val target = it.target as HTMLFormElement
            props.onSubmit?.invoke(FormData(target).get("preciseInput") / 100)
        }
        DialogContent {
            TextField {
                name = "preciseInput"
                type = InputType.number
                this.inputProps = jsObject {
                    this.min = 0
                    this.max = 100
                    this.step = 1
                }.unsafeCast<InputBaseComponentProps>()
                this.defaultValue = props.value * 100
                autoFocus = true
                this.asDynamic().InputProps = buildObject<InputProps> {
                    endAdornment = InputAdornment.create {
                        position = InputAdornmentPosition.end
                        +"%"
                    }
                }
            }
        }
        DialogActions {
            Button {
                onClick = { props.onCancel?.invoke() }
                +"Cancel"
            }
            Button {
                type = ButtonType.submit
                disabled = props.disabled
                +"Set"
            }
        }
    }
}