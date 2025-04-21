package components.redesign.forms

import browser.window
import components.redesign.basic.*
import csstype.*
import dom.html.*
import hooks.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.textarea
import tools.confido.utils.toFixed
import utils.except
import kotlin.math.pow

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
external interface MultilineTextInputProps: TextareaHTMLAttributes<HTMLTextAreaElement> {
    var autoHeight: Boolean?
    var autoHeightMin: Int?
    var autoHeightMax: Int?
}
val MultilineTextInput = FC<MultilineTextInputProps> { props->
    val theme = useTheme()

    fun autoResize(textarea: HTMLTextAreaElement) {
        val compStyle = window.asDynamic().getComputedStyle(textarea)
        fun getPx(s: dynamic) = if (s == null)  { 0.0 } else {  s.toString().replace("px","").trim().toDouble() }
        val lineHeight = getPx(compStyle.lineHeight)

        // Get min and max valraints
        val minRows = props.autoHeightMin ?: 2
        val maxRows = props.autoHeightMax ?: 8

        // Calculate min and max heights
        val extraHeight = getPx(compStyle.paddingTop) +
                            getPx(compStyle.paddingBottom) +
                            getPx(compStyle.borderTopWidth) +
                            getPx(compStyle.borderBottomWidht)

        val minHeight = (minRows * lineHeight) + extraHeight
        val maxHeight = (maxRows * lineHeight) + extraHeight

        textarea.style.height = "auto"

        val newHeight = minOf(maxOf(textarea.scrollHeight.toDouble(), minHeight), maxHeight);
        textarea.style.height = "${newHeight}px"

        textarea.style.overflowY = if (textarea.scrollHeight.toDouble() > maxHeight)  {"auto"} else {"hidden"}
    }
    val setInitialHeight = useRefEffect<HTMLTextAreaElement> {
        autoResize(current)
    }
    val onInput = useEventListener<HTMLTextAreaElement>("input") { ev->
        autoResize(ev.target as HTMLTextAreaElement)
    }
    textarea {
        +props

        if (props.autoHeight == true) {
            ref = combineRefs(setInitialHeight, onInput)
        }

        css(textInputClass, override=props) {
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
external interface InputProps<T>: PropsWithClassName {
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
    var decimals: Int?
}
val NumericInput = FC<NumericInputProps>("NumericInput") { props ->
    val decimals = props.decimals ?: 1
    val step = props.step ?: 0.1.pow(decimals)

    var rawValue by useState(props.value?.toFixed(decimals) ?: "")
    val ism = useInputStateManager(props) { rawValue = it?.toFixed(decimals) ?: "" }

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
        className =  props.className
        type = InputType.number
        // TODO: Proper step
        //step = kotlin.math.min(0.1, props.space.binner.binSize)
        this.value = rawValue
        placeholder = props.placeholder
        required = props.required
        props.min?.let { this.min = it }
        props.max?.let { this.max = it }
        this.step = step
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
