package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label

external interface OptionGroupProps<T>: PropsWithClassName {
    var defaultValue: T?
    var value: T?
    var options: List<Pair<T, String>>
    var onChange: ((T) -> Unit)?
}

inline fun <reified T> OptionGroup() = OptionGroupComponent as FC<OptionGroupProps<T>>

val OptionGroupComponent = FC<OptionGroupProps<dynamic>> { props ->
    var currentValue by useState<dynamic>(props.defaultValue)
    val realValue = props.value ?: currentValue

    Stack {
        direction = FlexDirection.row
        css(props.className) {
            padding = 2.px
            gap = 2.px
            justifyContent = JustifyContent.stretch
            borderRadius = 5.px
            backgroundColor = Color("#C2C0C6")
            width = 100.pct
        }

        props.options.map {(option, label) ->
            val optionStr = option.toString()
            val checked = realValue == option

            label {
                key = optionStr
                css {
                    flexGrow = number(1.0)
                    fontFamily = FontFamily.sansSerif
                    textAlign = TextAlign.center
                    fontSize = 17.px
                    lineHeight = 20.px
                    padding = 10.px
                    borderRadius = 3.px
                    cursor = Cursor.pointer

                    if (checked) {
                        backgroundColor = Color("#ffffff")
                        color = Color("#000000")
                    } else {
                        color = rgba(0,0,0,0.5)
                    }
                }

                input {
                    value = optionStr
                    this.checked = checked;
                    type = InputType.radio
                    css {
                        display = None.none
                    }
                    onChange = { currentValue = option; props.onChange?.invoke(option) }
                }
                +label
            }
        }
    }
}
