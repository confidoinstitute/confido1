package components.redesign.basic

import csstype.*
import emotion.css.*
import kotlinx.browser.*
import dom.html.*
import kotlinx.js.asList
import react.dom.events.*
import react.dom.events.MouseEvent
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
    position = Position.relative
    overflow = Overflow.hidden
}

fun <T: HTMLElement, E: NativeMouseEvent> createRipple(event: MouseEvent<T, E>, rippleColor: Color) {
    val element = event.currentTarget

    val ripple: HTMLSpanElement = document.createElement("span") as HTMLSpanElement
    val diameter = max(element.clientHeight, element.clientWidth)

    ripple.style.width = "${diameter}px"
    ripple.style.height = "${diameter}px"
    val offsetLeft = event.pageX - (element.offsetLeft + diameter/2)
    ripple.style.left = "${offsetLeft}px"
    val offsetTop = event.pageY - (element.offsetTop + diameter/2)
    ripple.style.top = "${offsetTop}px"
    ripple.classList.add("ripple", rippleClass(rippleColor).toString())

    element.getElementsByClassName("ripple").asList().map { it.remove() }
    element.appendChild(ripple)
}
