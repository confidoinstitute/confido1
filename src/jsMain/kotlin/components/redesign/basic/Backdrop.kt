package components.redesign.basic

import browser.document
import csstype.*
import dom.html.HTMLDivElement
import emotion.css.keyframes
import emotion.react.Global
import emotion.react.css
import emotion.react.styles
import react.FC
import react.Props
import react.create
import react.dom.createPortal
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML

internal val fadeKF = keyframes {
    0.pct {
        backdropFilter = None.none
        opacity = number(0.0)
    }
    100.pct {
        opacity = number(1.0)
    }
}

val Backdrop = FC<HTMLAttributes<HTMLDivElement>> {
    Global {
        styles {
            "body" {
                overflow = Overflow.hidden
            }
        }
    }
    +createPortal(
    ReactHTML.div.create {
        +it
        css(override=it) {
            position = Position.fixed
            top = 0.px
            width = 100.pct
            height = 100.pct
            overflow = Overflow.hidden
            backgroundColor = rgba(0, 0, 0, 0.5)
            zIndex = integer(2000)
            animationName = fadeKF
            animationDuration = 0.25.s
            animationTimingFunction = AnimationTimingFunction.easeOut
        }
    }, document.body.asDynamic())
}
