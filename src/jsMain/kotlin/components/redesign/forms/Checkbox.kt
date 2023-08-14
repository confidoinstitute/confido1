package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import dom.html.*
import emotion.react.*
import kotlinx.js.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg

external interface CheckboxProps: InputHTMLAttributes<HTMLInputElement>, PropsWithPalette<PaletteWithText> {
    var alwaysColorBackground: Boolean
    var mask: Mask?
    var maskColor: Color?
    var noCheckmark: Boolean?
}

val Checkbox = FC<CheckboxProps> { props ->
    val palette = props.palette ?: MainPalette.primary
    val checkboxRef = useRef<HTMLInputElement>()

    val checked = checkboxRef.current?.checked ?: false
    val noCheckmark = props.noCheckmark ?: false

    label {
        css(override=props.className) {
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
                display = None.none
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

                // XXX unchecked checkbox is "invisible"
                if (props.alwaysColorBackground)
                    backgroundColor = palette.color
                //else
                //    backgroundColor = Color("#DDDDDD")

                cursor = Cursor.pointer

                "input:checked ~ &" {
                    backgroundColor = palette.color
                }

                "input:disabled ~ &" {
                    opacity = number(0.3)
                    filter = saturate(0.1)
                }
            }
            props.mask?.let { mask ->
                span {
                    css {
                        position = Position.absolute
                        bottom = 5.px
                        left = 5.px
                        right = 5.px
                        top = 5.px
                        this.mask = mask
                        maskSize = MaskSize.contain
                        backgroundColor = props.maskColor
                    }
                }
            }
        }

        if (!noCheckmark) {
            svg {
                css {
                    position = Position.absolute
                    left = 8.px
                    top = 9.px
                    opacity = number(0.0)
                    cursor = Cursor.pointer
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
                    d =
                        "M0.939341 5.06066L0.56066 5.43934C-0.0251262 6.02513 -0.0251252 6.97487 0.560661 7.56066L3.93934 10.9393C4.52513 11.5251 5.47487 11.5251 6.06066 10.9393L13.4393 3.56066C14.0251 2.97487 14.0251 2.02513 13.4393 1.43934L13.0607 1.06066C12.4749 0.474874 11.5251 0.474872 10.9393 1.06066L5 7L3.06066 5.06066C2.47487 4.47487 1.52513 4.47487 0.939341 5.06066Z"
                }
            }
        }
    }
}

external interface SwitchProps: InputHTMLAttributes<HTMLInputElement>, PropsWithPalette<PaletteWithText> {
    var switchHeight: Double?
    var switchWidth: Double?
    var offIcon: ReactNode?
    var onIcon: ReactNode?
    var activeIconColor: Color?
    var inactiveIconColor: Color?
    var noColor: Boolean?
}
val Switch = FC<SwitchProps> { props ->
    val palette = props.palette ?: MainPalette.primary
    val switchHeight = props.switchHeight ?: 36.0
    val switchWidth = props.switchWidth ?: 64.0
    val activeIconColor = props.activeIconColor ?: Color("#555555")
    val inactiveIconColor = props.inactiveIconColor ?: Color("#555555").addAlpha("30%")
    label {
        css {
            width = switchWidth.px
            height = switchHeight.px
            display = Display.inlineBlock
            position = Position.relative
            flexShrink = number(0.0)
        }
        input {
            +props
            type = InputType.checkbox
            css {
                display = None.none
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
                borderRadius = (switchHeight / 2.0).px
                transitionDuration = 0.4.s
                cursor = Cursor.pointer

                before {
                    left = 2.px
                    top = 2.px
                    width = (switchHeight - 4.0).px
                    height = (switchHeight - 4.0).px
                    borderRadius = 100.pct
                    backgroundColor = Color("#FFFFFF")
                    content = string("\"\"")
                    position = Position.absolute
                    transitionDuration = 0.4.s
                }

                "input:checked + &" {
                    if (!(props.noColor ?: false))
                        backgroundColor = palette.color
                    before {
                        left = (switchWidth - switchHeight + 2).px
                    }
                }

                "input:disabled + &" {
                    opacity = number(0.3)
                    filter = saturate(0.1)
                }
            }
        }
        props.offIcon?.let { offIcon ->
            span {
                css {
                    position = Position.absolute
                    bottom = 2.px
                    left = 2.px
                    width = (switchHeight - 4.0).px
                    top = 2.px
                    display = Display.flex
                    alignItems = AlignItems.center
                    justifyContent = JustifyContent.center

                    color =  activeIconColor

                    "input:checked ~ &" {
                        color =  inactiveIconColor
                    }
                }
                +offIcon
            }
        }
        props.onIcon?.let { onIcon ->
            span {
                css {
                    position = Position.absolute
                    bottom = 2.px
                    right = 2.px
                    width = (switchHeight - 4.0).px
                    top = 2.px
                    display = Display.flex
                    alignItems = AlignItems.center
                    justifyContent = JustifyContent.center
                    color =  inactiveIconColor

                    "input:checked ~ &" {
                        color =  activeIconColor
                    }
                }
                +onIcon
            }
        }
    }
}

