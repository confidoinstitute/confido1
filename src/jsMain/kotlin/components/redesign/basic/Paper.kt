package components.redesign.basic

import csstype.*
import dom.html.*
import emotion.react.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div

val Paper = FC<HTMLAttributes<HTMLDivElement>> { props ->
    div {
        +props
        css(props.className) {
            maxWidth = 400.px
            boxShadow = BoxShadow(0.px, 0.px, 10.px, Color("#CCCCCC"))
        }
    }
}
