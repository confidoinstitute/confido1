package components.redesign.basic

import csstype.*
import dom.Element
import emotion.css.*
import kotlinx.browser.*
import dom.html.*
import emotion.react.css
import kotlinx.js.asList
import react.ComponentType
import react.FC
import react.dom.DOMAttributes
import react.dom.events.*
import react.dom.events.MouseEvent
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML.button
import utils.except
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

fun <T: Element, E: NativeMouseEvent> createRipple(event: MouseEvent<T, E>, rippleColor: Color) {
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
    ripple.classList.add("ripple", rippleClass(rippleColor).toString())

    element.getElementsByClassName("ripple").asList().map { it.remove() }
    element.appendChild(ripple)
}