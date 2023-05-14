package components.redesign.basic

import csstype.*
import dom.html.*
import emotion.react.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div

val Alert = FC<HTMLAttributes<HTMLDivElement>> {
    div {
        +it
        css(it.className) {
            marginTop = 20.px
            marginBottom = 5.px
            padding = Padding(10.px, 12.px)
            textAlign = TextAlign.center
            backgroundColor = Color("#a327d7")  // TODO(Prin): Use palette.
            color = Color("#FFFFFF")
            borderWidth = 0.px
            borderRadius = 5.px
            width = 100.pct

            fontSize = 15.px
            lineHeight = 18.px
            fontFamily = sansSerif
        }
    }
}