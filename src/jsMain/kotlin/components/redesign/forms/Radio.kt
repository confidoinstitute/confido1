package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import dom.html.*
import emotion.react.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span

external interface RadioInputProps: InputHTMLAttributes<HTMLInputElement>, PropsWithPalette<Palette>

val RadioInput = FC<RadioInputProps> { props ->
    val palette = props.palette ?: MainPalette.primary
    label {
        css {
            display = Display.inlineBlock
            width = 32.px
            height = 32.px
        }
        input {
            +props
            type = InputType.radio
            css {
                display = None.none
            }
        }
        span {
            css {
                display = Display.inlineBlock
                width = 32.px
                height = 32.px
                boxSizing = BoxSizing.borderBox
                borderRadius = 100.pct
                border = Border(16.px, LineStyle.solid, Color("#DDDDDD"))
                backgroundColor = Color("#DDDDDD")
                cursor = Cursor.pointer
                transition = 0.4.s

                "input:checked + &" {
                    border = Border(10.px, LineStyle.solid, palette.color)
                    backgroundColor = Color("#FFFFFF")
                }

                "input:disabled + &" {
                    opacity = number(0.3)
                    filter = saturate(0.1)
                }
            }
        }
    }
}

external interface RadioGroupProps<T>: Props {
    var title: String
    var defaultValue: T?
    var value: T?
    var options: List<Pair<T, String>>
    var disabled: Boolean
    var onChange: ((T) -> Unit)?
}

inline fun <reified T> RadioGroup() = RadioGroupComponent as FC<RadioGroupProps<T>>

val RadioGroupComponent = FC<RadioGroupProps<dynamic>> { props ->
    var currentValue by useState<dynamic>(props.defaultValue)
    val realValue = props.value ?: currentValue


    BinaryFormGroup {
        title = props.title

        props.options.map { (option, label) ->
            val optionStr = option.toString()
            val checked = realValue == option

            BinaryFormField {
                key = optionStr
                disabled = props.disabled
                input = RadioInput.create {
                        value = optionStr
                        this.checked = checked
                        this.disabled = props.disabled
                        onChange = { currentValue = option; props.onChange?.invoke(option) }
                    }
                +label
            }
        }
    }
}
