package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span

external interface FormFieldProps : PropsWithChildren, PropsWithClassName {
    var title: String
    var titleColor: Color?
    var comment: String
    var error: String?
    var required: Boolean?
}

val FormField = FC<FormFieldProps> { props ->
    val notesClass = ClassName("FormGroup-Notes")

    Stack {
        direction = FlexDirection.column
        css(props.className) {
            alignItems = AlignItems.stretch
            padding = 0.px
            gap = 7.px
        }

        // Title and required
        Stack {
            direction = FlexDirection.row
            css(notesClass) {
                gap = 7.px
                fontFamily = sansSerif
                fontSize = 14.px
                lineHeight = 17.px
                color = props.titleColor
            }

            span {
                css {
                    fontWeight = integer(500)
                    flexGrow = number(1.0)
                }
                +props.title
            }
            if (props.required == true) {
                span {
                    css {
                        color = Color("#F35454")
                        textAlign = TextAlign.right
                    }
                    +"Required"
                }
            }
        }

        // Input elements
        Stack {
            direction = FlexDirection.row
            css {
                gap = 10.px
            }
            +props.children
        }

        // Error area
        props.error?.let {
            div {
                css {
                    color = Color("#F35454")
                    fontFamily = sansSerif
                    fontSize = 12.px
                    lineHeight = 14.px
                }
                +it
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
