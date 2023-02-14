package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import dom.html.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.col
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span

external interface FormSwitchProps : InputHTMLAttributes<HTMLInputElement>, PropsWithPalette<Palette> {
    var component: FC<CheckboxProps>?
    var label: String
    var comment: String
}

val FormSwitch = FC<FormSwitchProps> { props ->
    Stack {
        direction = FlexDirection.column
        css {
            gap = 2.px
        }

        Stack {
            component = label
            direction = FlexDirection.row
            css {
                alignItems = AlignItems.center
            }
            div {
                css {
                    if (props.disabled == true)
                        color = Color("#DDDDDD")
                    fontFamily = FontFamily.sansSerif
                    fontSize = 18.px
                    flexGrow = number(1.0)
                }
                +props.label
            }
            (props.component ?: Switch) {
                +props
            }
        }

        // Comment area
        div {
            css {
                color = Color("#AAAAAA")
                fontFamily = FontFamily.sansSerif
                fontSize = 12.px
                lineHeight = 14.px
            }
            +props.comment
        }
    }
}