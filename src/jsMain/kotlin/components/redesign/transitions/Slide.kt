package components.redesign.transitions

import csstype.*
import emotion.react.Global
import emotion.react.styles
import ext.reacttransitiongroup.CSSTransition
import ext.reacttransitiongroup.TransitionProps
import react.FC

enum class SlideDirection {
    up,
    down,
    left,
    right,
}

external interface SlideProps : TransitionProps {
    var direction: SlideDirection
}

fun PropertiesBuilder.slideDirection(dir: SlideDirection) = when(dir) {
    SlideDirection.up -> transform = translatey(100.pct)
    SlideDirection.down -> transform = translatey((-100).pct)
    SlideDirection.left -> transform = translatex((-100).pct)
    SlideDirection.right -> transform = translatex(100.pct)
}

val Slide = FC<SlideProps> {props ->
    val className = emotion.css.ClassName {
        "&-enter, &-appear" {
            slideDirection(props.direction)
        }
        "&-enter-active, &-appear-active" {
            transform = None.none
            transitionProperty = "transform".asDynamic()
            transitionDuration = (props.timeout).ms
            transitionTimingFunction = TransitionTimingFunction.easeOut
        }
        "&-exit" {
            transform = None.none
        }
        "&-exit-active" {
            slideDirection(props.direction)
            transitionProperty = "transform".asDynamic()
            transitionDuration = (props.timeout).ms
            transitionTimingFunction = TransitionTimingFunction.easeIn
        }
    }
    CSSTransition {
        +props
        classNames = className.toString()
    }
}
