package components.redesign.basic

import csstype.*
import dom.*
import dom.html.*
import emotion.css.*
import kotlinx.browser.*
import kotlinx.js.*
import react.*
import react.dom.events.*
import react.dom.html.*
import utils.*
import kotlin.math.*

internal val rippleKeyframes = keyframes {
    100.pct {
        transform = scale(2)
        opacity = number(0.0)
    }
}

internal fun rippleClass(rippleColor: Color) = ClassName {
    position = Position.absolute
    borderRadius = 50.pct
    transform = scale(0)
    animationName = rippleKeyframes
    animationDuration = 800.ms
    animationTimingFunction = AnimationTimingFunction.easeOut
    backgroundColor = rippleColor
    opacity = number(0.3)
}

fun PropertiesBuilder.rippleCss() {
    "&&" {
        position = Position.relative
        overflow = Overflow.hidden
    }
}

fun <T: Element, E: NativeMouseEvent> createRipple(event: MouseEvent<T, E>, rippleColor: Color? = null) {
    val element = event.currentTarget

    val ripple: HTMLSpanElement = document.createElement("span") as HTMLSpanElement
    val diameter = max(element.clientHeight, element.clientWidth)
    val rect = element.getBoundingClientRect()

    ripple.style.width = "${diameter}px"
    ripple.style.height = "${diameter}px"
    val offsetLeft = event.clientX - (rect.left + diameter/2)
    ripple.style.left = "${offsetLeft}px"
    val offsetTop = event.clientY - (rect.top + diameter/2)
    ripple.style.top = "${offsetTop}px"
    ripple.classList.add("ripple")
    rippleColor?.let {
        ripple.classList.add(rippleClass(rippleColor).toString())
    }

    element.getElementsByClassName("ripple").asList().map { it.remove() }
    element.appendChild(ripple)
}


fun <P: HTMLAttributes<E>, E: HTMLElement> ElementType<P>.withRipple() = FC<P> {props ->
    this@withRipple {
        +props.except("rippleColor")
        css(override = props) {
            rippleCss()
            ".ripple" {
                position = Position.absolute
                borderRadius = 50.pct
                transform = scale(0)
                animationName = rippleKeyframes
                animationDuration = 800.ms
                animationTimingFunction = AnimationTimingFunction.easeOut
                opacity = number(0.3)
            }
        }

        onClick = {
            createRipple(it)
            props.onClick?.invoke(it)
        }
    }
}