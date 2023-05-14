package components.redesign.transitions

import csstype.*
import ext.reacttransitiongroup.*
import react.*

val Fade = FC<TransitionProps> {props ->
    val className = emotion.css.ClassName {
        "&-enter, &-appear" {
            opacity = number(0.0)
        }
        "&-enter-active, &-appear-active" {
            opacity = number(1.0)
            transitionProperty = "opacity".asDynamic()
            transitionDuration = (props.timeout).ms
            transitionTimingFunction = TransitionTimingFunction.easeOut
        }
        "&-exit" {
            opacity = number(1.0)
        }
        "&-exit-active" {
            opacity = number(0.0)
            transitionProperty = "opacity".asDynamic()
            transitionDuration = (props.timeout).ms
            transitionTimingFunction = TransitionTimingFunction.easeIn
        }
    }
    CSSTransition {
        +props
        classNames = className.toString()
    }
}
