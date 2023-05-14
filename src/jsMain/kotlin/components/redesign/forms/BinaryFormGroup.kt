package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.label

external interface BinaryFormFieldProps: PropsWithChildren {
    var input: ReactNode
    var disabled: Boolean
}

val BinaryFormField = FC<BinaryFormFieldProps> { props ->
    Stack {
        direction = FlexDirection.row
        component = label
        css {
            margin = Margin(10.px, 20.px)
            gap = 16.px
            alignItems = AlignItems.center
        }
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                width = 32.px
                height = 32.px
            }
            +props.input
        }
        ReactHTML.span {
            css {
                fontFamily = sansSerif
                fontSize = 17.px
                lineHeight = 21.px
                if (props.disabled) {
                    color = Color("#DDDDDD")
                }
            }
            +props.children
        }
    }
}

external interface BinaryFormGroupProps: PropsWithChildren {
    var title: String
}

val BinaryFormGroup = FC<BinaryFormGroupProps> { props ->

    Stack {
        direction = FlexDirection.column
        css {
            gap = 10.px
        }
        ReactHTML.div {
            css {
                fontFamily = sansSerif
                fontSize = 18.px
                lineHeight = 21.px
                flexGrow = number(1.0)
            }
            +props.title
        }

        Stack {
            direction = FlexDirection.column

            +props.children
        }
    }
}
