package components.redesign.basic

import csstype.BoxSizing
import csstype.px
import emotion.react.styles
import react.*

val GlobalCss = FC<Props> {
    emotion.react.Global {
        styles {
            "*" {
                boxSizing = BoxSizing.borderBox
            }
            "html,body" {
                margin = 0.px
                padding = 0.px
            }
        }
    }
}
