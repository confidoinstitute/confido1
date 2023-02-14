package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import kotlinx.js.*
import org.w3c.dom.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg

external interface CheckboxProps: InputHTMLAttributes<HTMLInputElement>, PropsWithPalette<PaletteWithText> {
    var alwaysColorBackground: Boolean
}

val Checkbox = FC<CheckboxProps> { props ->
    val palette = props.palette ?: MainPalette.primary
    val checkboxRef = useRef<HTMLInputElement>()

    val checked = checkboxRef.current?.checked ?: false
    console.log(checked)

    label {
        css {
            position = Position.relative
            display = Display.inlineBlock
            width = 30.px
            height = 30.px
            flexShrink = number(0.0)
        }

        input {
            ref = checkboxRef
            +props
            delete(asDynamic().alwaysColorBackground)
            type = InputType.checkbox
            css {
                display = "none".asDynamic()
            }
        }

        span {
            css {
                position = Position.absolute
                bottom = 0.px
                left = 0.px
                right = 0.px
                top = 0.px
                borderRadius = 5.px
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                transition = 0.1.s

                if (props.alwaysColorBackground)
                    backgroundColor = palette.color
                else
                    backgroundColor = Color("#DDDDDD")

                cursor = Cursor.pointer

                "input:checked ~ &" {
                    backgroundColor = palette.color
                }

                "input:disabled ~ &" {
                    backgroundColor = Color("#EBEBEB")
                    cursor = "initial".asDynamic()
                }
            }
        }

        svg {
            css {
                position = Position.absolute
                left = 8.px
                top = 9.px
                opacity = number(0.0)
                transition = 0.1.s
                "input:checked ~ &" {
                    opacity = number(1.0)
                }
            }
            width = 14.0
            height = 12.0
            viewBox = "0 0 14 12"
            fill = palette.text.color.toString()
            path {
                d = "M0.939341 5.06066L0.56066 5.43934C-0.0251262 6.02513 -0.0251252 6.97487 0.560661 7.56066L3.93934 10.9393C4.52513 11.5251 5.47487 11.5251 6.06066 10.9393L13.4393 3.56066C14.0251 2.97487 14.0251 2.02513 13.4393 1.43934L13.0607 1.06066C12.4749 0.474874 11.5251 0.474872 10.9393 1.06066L5 7L3.06066 5.06066C2.47487 4.47487 1.52513 4.47487 0.939341 5.06066Z"
            }
        }
    }
}

val Switch = FC<CheckboxProps> { props ->
    val palette = props.palette ?: MainPalette.primary
    label {
        css {
            width = 64.px
            height = 36.px
            display = Display.inlineBlock
            position = Position.relative
        }
        input {
            +props
            delete(asDynamic().alwaysColorBackground)
            type = InputType.checkbox
            css {
                display = "none".asDynamic()
            }
        }
        span {
            css {
                position = Position.absolute
                bottom = 0.px
                left = 0.px
                right = 0.px
                top = 0.px
                backgroundColor = Color("#C2C0C6")
                borderRadius = 18.px
                transitionDuration = 0.4.s
                cursor = Cursor.pointer

                before {
                    left = 2.px
                    top = 2.px
                    width = 32.px
                    height = 32.px
                    borderRadius = 100.pct
                    backgroundColor = Color("#FFFFFF")
                    content = "\"\"".asDynamic()
                    position = Position.absolute
                    transitionDuration = 0.4.s
                }

                "input:checked + &" {
                    backgroundColor = palette.color
                    before {
                        left = 30.px
                    }
                }

                "input:disabled + &" {
                    backgroundColor = Color("#EBEBEB")
                    cursor = "initial".asDynamic()
                }
            }
        }
    }
}

