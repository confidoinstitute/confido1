package components

import dom.html.HTMLFormElement
import kotlinx.datetime.LocalDate
import kotlinx.js.jso
import mui.material.*
import react.*
import react.dom.html.ButtonType
import react.dom.html.InputType
import react.dom.html.ReactHTML
import tools.confido.utils.fromUnix
import utils.numericInputProps
import web.http.FormData

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
                this.inputProps = numericInputProps(props.min, props.max, props.step)
                this.defaultValue = props.value
                autoFocus = true
                this.asDynamic().InputProps = jso<InputProps> {
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
                this.inputProps = numericInputProps(props.min, props.max, null)
                this.defaultValue = LocalDate.fromUnix(props.value).toString()
                autoFocus = true
                if (props.unit.isNotEmpty())
                this.asDynamic().InputProps = jso<InputProps> {
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
            val number = (FormData(target).get("preciseInput") as String).toDouble()
            props.onSubmit?.invoke(number / 100)
        }
        DialogContent {
            TextField {
                name = "preciseInput"
                type = InputType.number
                this.inputProps = numericInputProps(0.0, 100.0, 1.0)
                this.defaultValue = props.value * 100
                autoFocus = true
                this.asDynamic().InputProps = jso<InputProps> {
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