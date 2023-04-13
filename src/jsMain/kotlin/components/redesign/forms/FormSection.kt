package components.redesign.forms

import components.redesign.basic.*
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div

external interface FormSectionProps : PropsWithChildren, PropsWithClassName {
    var title: String?
}

val FormDivider = FC<PropsWithChildren> {
    div {
        css {
            backgroundColor = Color("#F2F2F2")
            color = Color("#777777")
            border = Border(0.px, LineStyle.solid, Color("#CCCCCC"))
            borderTopWidth = 0.5.px
            borderBottomWidth = 0.5.px

            fontFamily = sansSerif
            fontSize = 13.px
            lineHeight = 16.px
            textTransform = TextTransform.uppercase
            padding = Padding(12.px, 15.px)
        }
        +it.children
    }
}

val FormSection = FC<FormSectionProps> { props ->
    props.title?.let {
        FormDivider {
            +it
        }
    }
    Stack {
        direction = FlexDirection.column
        css {
            backgroundColor = Color("#FFFFFF")
            gap = 20.px
            padding = 20.px
        }
        +props.children
    }
}