package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.css.*
import emotion.react.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label

data class OptionGroupVariant(
    val stackCSS: ClassName,
    val optionCSS: (checked: Boolean, disabled: Boolean)->ClassName,
)

external interface OptionGroupProps<T>: PropsWithClassName {
    var defaultValue: T?
    var value: T?
    var options: List<Pair<T, String>>
    var disabled: Boolean
    var onChange: ((T) -> Unit)?
    var variant: OptionGroupVariant?
}

val  OptionGroupFormVariant = OptionGroupVariant(
    stackCSS = ClassName {
        padding = 2.px
        gap = 2.px
        justifyContent = JustifyContent.stretch
        borderRadius = 5.px
        backgroundColor = Color("#C2C0C6")
        width = 100.pct
    },
    optionCSS = { checked, disabled -> ClassName{
        flexGrow = number(1.0)
        fontFamily = sansSerif
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

        ".ripple" {
            backgroundColor = Color("#000000")
        }

        if (disabled) {
            opacity = number(0.3)
            filter = saturate(0.1)
        }
    } }
)

val OptionGroupPageTabsVariant = OptionGroupVariant(
    stackCSS = ClassName {
        padding = Padding(15.px, 0.px)
        paddingTop = 0.px
        borderRadius = 5.px
        flexShrink = number(0.0)
    },

    optionCSS = { checked, disabled ->
        ClassName {
            cursor = Cursor.pointer

            borderRadius = 10.px
            padding = Padding(12.px, 10.px)

            flexGrow = number(1.0)
            textAlign = TextAlign.center

            fontFamily = sansSerif
            fontSize = 17.px
            lineHeight = 21.px

            ".ripple" {
                backgroundColor = Color("#DDDDDD")
            }

            if (checked) {
                backgroundColor = Color("#FFFFFF")
                color = Color("#000000")
                fontWeight = integer(500)
            } else {
                hover {
                    backgroundColor = Color("#DDDDDD")
                }

                color = Color("rgba(0, 0, 0, 0.5)")
                fontWeight = integer(400)
            }
        }
    }
)

inline fun <reified T> OptionGroup() = OptionGroupComponent as FC<OptionGroupProps<T>>

private val RippleLabel = label.withRipple()

val OptionGroupComponent = FC<OptionGroupProps<dynamic>> { props ->
    var currentValue by useState<dynamic>(props.defaultValue)
    val realValue = props.value ?: currentValue
    val variant = props.variant ?: OptionGroupFormVariant

    Stack {
        direction = FlexDirection.row
        css(variant.stackCSS, override=props.className) {
        }

        props.options.map {(option, label) ->
            val optionStr = option.toString()
            val checked = realValue == option

            RippleLabel {
                key = optionStr
                className = variant.optionCSS(checked, props.disabled)

                input {
                    value = optionStr
                    this.checked = checked;
                    type = InputType.radio
                    css {
                        display = None.none
                    }
                    disabled = props.disabled
                    onChange = {
                        if (!props.disabled) {
                            currentValue = option
                            props.onChange?.invoke(option)
                        }
                    }
                }
                +label
            }
        }
    }
}
