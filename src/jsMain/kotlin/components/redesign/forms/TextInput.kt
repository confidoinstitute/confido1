package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import dom.html.*
import hooks.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.textarea
import utils.except

val textInputClass = emotion.css.ClassName {
    padding = Padding(10.px, 12.px)
    borderWidth = 0.px
    borderRadius = 5.px
    width = 100.pct

    fontSize = 17.px
    lineHeight = 20.px
    fontFamily = sansSerif
    resize = None.none

    focus {
        outline = Outline(2.px, LineStyle.solid, MainPalette.primary.color)
    }

    placeholder {
        color = Color("#BBBBBB")
    }

    disabled {
        opacity = number(0.3)
        filter = saturate(0.1)
    }
}

val TextInput = FC<InputHTMLAttributes<HTMLInputElement>> {
    val theme = useTheme()
    input {
        +it

        css(textInputClass, override=it) {
            backgroundColor = theme.colors.form.inputBackground
        }
    }
}
val MultilineTextInput = FC<TextareaHTMLAttributes<HTMLTextAreaElement>> {
    val theme = useTheme()
    textarea {
        +it

        css(textInputClass, override=it) {
            backgroundColor = theme.colors.form.inputBackground
        }
    }
}

sealed class InputError {
    override fun toString() = "Invalid input"
}
object InvalidFormat : InputError(){
    override fun toString() = "Invalid input format"
}
object RequiredInputMissing: InputError(){
    override fun toString() = "Required"
}
sealed class InputOutOfRange: InputError(){
    override fun toString() = "Value out of allowed range"
}
class InputTooLarge(val limit: Any): InputOutOfRange() {
    override fun toString() = "Value too large (maximum: $limit)"
}
class InputTooSmall(val limit: Any): InputOutOfRange() {
    override fun toString() = "Value too small (minimum: $limit)"
}
external interface InputProps<T>: Props {
    var value: T?
    var placeholder: String?
    var onChange: ((newVal: T?, errType: InputError?) -> Unit)?
    var required: Boolean?
    var disabled: Boolean?
    var readOnly: Boolean?
    var inFormField: Boolean?
}

external interface InputPropsWithRange<T>: InputProps<T> {
    var min: T?
    var max: T?
}

abstract class InputStateManager<T: Any> {
    abstract val current: T?
    abstract fun update(newValue: T?, err: InputError?)
}
inline fun <reified T: Any, P: InputProps<T>>
useInputStateManager(props: P,
                           stringify: ((T?)->String) = { it.toString() },
                           crossinline setInternalState: (T?)->Unit = {}):
                    InputStateManager<T> {
    // Last value known to be seen by the outside world - either because parent
    // gave it to us via props or we gave it to parent via onChange.
    var lastOutsideValue by useState(props.value)
    var internalValue by useState(props.value)
    var lastError by useState<InputError>()
    useEffectNotFirst(stringify(props.value)) {
        if (props.value != lastOutsideValue) {
            setInternalState(props.value)
            internalValue = props.value
            lastOutsideValue = props.value
        }
    }
    return object: InputStateManager<T>() {
        override val current = internalValue
        override fun update(newValue: T?, err: InputError?) {
            // Make sure we do not update when we get our own value back
            // Especially useful in error conditions, we push null through
            // onChange and get null back from parent, but we do not want to
            // update the internal state because that would clear the invalid
            // (perhaps partial) value.
            internalValue = newValue
            if (newValue != lastOutsideValue || err != lastError) {
                lastOutsideValue = newValue
                lastError = err
                if (newValue == null && props.required == true)
                    props.onChange?.invoke(null, RequiredInputMissing)
                else
                    props.onChange?.invoke(newValue, err)
            }
        }
    }
}

external interface NumericInputProps : InputPropsWithRange<Double> {
    var step: Double?
}
val NumericInput = FC<NumericInputProps>("NumericInput") { props ->
    var rawValue by useState(props.value?.toString() ?: "")
    val ism = useInputStateManager(props) { rawValue = it?.toString() ?: "" }

    fun onRawUpdate(newRaw: String) {
        val newValue = newRaw.toDoubleOrNull()
        if (newRaw.isEmpty())
            ism.update(null, null)
        else if (newValue == null)
            ism.update(null, InvalidFormat)
        else if (newValue < (props.min ?: Double.NEGATIVE_INFINITY))
            ism.update(null, InputTooSmall(props.min!!))
        else if (newValue > (props.max ?: Double.POSITIVE_INFINITY))
            ism.update(null, InputTooLarge(props.max!!))
        else
            ism.update(newValue, null)
    }

    useEffectNotFirst(props.min, props.max, props.required) {
        onRawUpdate(rawValue)
    }

    TextInput {
        type = InputType.number
        // TODO: Proper step
        //step = kotlin.math.min(0.1, props.space.binner.binSize)
        this.value = rawValue
        placeholder = props.placeholder
        required = props.required
        props.min?.let { this.min = it }
        props.max?.let { this.max = it }
        props.step?.let { this.step = it }
        props.disabled?.let { this.disabled = it }
        props.readOnly?.let { this.readOnly = it }
        onChange = { event ->
            val newRaw = event.target.value
            rawValue = newRaw
            onRawUpdate(newRaw)
        }
    }
}

external interface InputFormFieldProps<T, P: InputProps<T>> : FormFieldProps {
    var inputComponent: ComponentType<P>
    var inputProps: P
}
inline fun <reified T, P: InputProps<T>> InputFormField() = InputFormFieldComponent.unsafeCast<FC<InputFormFieldProps<T, P>>>()

val InputFormFieldComponent = FC<InputFormFieldProps<dynamic, InputProps<dynamic>>> { props ->
    var inputError by useState<InputError>()
    FormField {
        +props.except("error", "inputComponent", "inputProps")
        this.error = props.error ?: inputError?.toString()
        props.inputComponent {
            +props.inputProps.except("onChange", "required", "inFormField")
            this.inFormField = true
            this.required = props.inputProps.required ?: props.required ?: false
            this.onChange = { v, err->
                inputError = err
                props.inputProps.onChange?.invoke(v, err)
            }
        }
    }
}
