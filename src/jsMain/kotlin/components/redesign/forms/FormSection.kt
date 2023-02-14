package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div

external interface FormSectionProps : PropsWithChildren, PropsWithClassName {
    var title: String
}

val FormSection = FC<FormSectionProps> { props ->
    div {
        css {
            backgroundColor = Color("#F2F2F2")
            color = Color("#777777")

            fontFamily = FontFamily.sansSerif
            fontSize = 13.px
            lineHeight = 16.px
            padding = Padding(12.px, 15.px)
        }

        +props.title
    }
    Stack {
        direction = FlexDirection.column
        css {
            border = Border(0.px, LineStyle.solid, Color("#CCCCCC"))
            borderTopWidth = 0.5.px
            borderBottomWidth = 0.5.px
            gap = 20.px
            padding = 20.px
        }
        +props.children
    }
}