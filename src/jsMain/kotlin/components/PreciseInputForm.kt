package components

import mui.material.*
import org.w3c.dom.HTMLFormElement
import org.w3c.xhr.FormData
import react.*
import react.dom.html.ButtonType
import react.dom.html.InputType
import react.dom.html.ReactHTML
import utils.jsObject

external interface PreciseInputFormProps : Props {
    var value: dynamic
    var min: dynamic
    var max: dynamic
    var step: dynamic
    var unit: String
    var onCancel: (() -> Unit)?
    var onSubmit: ((Double) -> Unit)?
}

val PreciseInputNumber = FC<PreciseInputFormProps> {props ->
    val adornment = InputAdornment.create {
        position = InputAdornmentPosition.end
        +props.unit
    }

    ReactHTML.form {
        onSubmit = {
            it.preventDefault()
            it.stopPropagation()
            val target = it.target as HTMLFormElement
            props.onSubmit?.invoke(FormData(target).get("preciseInput") as Double)
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
                this.asDynamic().InputProps = jsObject {
                    endAdornment = adornment
                }.unsafeCast<InputBaseComponentProps>()
            }
        }
        DialogActions {
            Button {
                onClick = { props.onCancel?.invoke() }
                +"Cancel"
            }
            Button {
                type = ButtonType.submit
                +"Set"
            }
        }
    }
}

val PreciseInputPercent = FC<PreciseInputFormProps> {props ->
    val adornment = InputAdornment.create {
        position = InputAdornmentPosition.end
        +"%"
    }

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
                this.asDynamic().InputProps = jsObject {
                    endAdornment = adornment
                }.unsafeCast<InputBaseComponentProps>()
            }
        }
        DialogActions {
            Button {
                onClick = { props.onCancel?.invoke() }
                +"Cancel"
            }
            Button {
                type = ButtonType.submit
                +"Set"
            }
        }
    }

}