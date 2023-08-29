package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import dom.html.*
import emotion.react.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.label
import utils.except

external interface FormSwitchProps : CheckboxProps {
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
                    fontFamily = sansSerif
                    fontSize = 18.px
                    flexGrow = number(1.0)
                }
                +props.label
            }
            (props.component ?: Switch) {
                +props.except("label", "comment")
            }
        }

        // Comment area
        div {
            css {
                color = Color("#AAAAAA")
                fontFamily = sansSerif
                fontSize = 12.px
                lineHeight = 14.px
            }
            +props.comment
        }
    }
}