package components.redesign.basic

import csstype.*
import emotion.react.styles
import react.*

external interface GlobalCssProps : Props {
    var backgroundColor: BackgroundColor
}

val GlobalCss = FC<GlobalCssProps> {props ->
    emotion.react.Global {
        styles {
            "*" {
                boxSizing = BoxSizing.borderBox
            }
            "html,body" {
                margin = 0.px
                padding = 0.px
                minHeight = 100.vh
                backgroundColor = props.backgroundColor
                display = Display.flex
                flexDirection = FlexDirection.column
            }
        }
    }
}
