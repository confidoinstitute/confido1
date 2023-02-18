package components.redesign.basic

import csstype.*
import emotion.react.*
import dom.html.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import utils.except


external interface StackProps : HTMLAttributes<HTMLElement> {
    var component: ElementType<*>?
    var direction: FlexDirection?
}

val Stack = ForwardRef<HTMLElement, StackProps> { props, fRef ->
    val component: ElementType<HTMLAttributes<HTMLElement>> = (props.component ?: div).unsafeCast<ElementType<HTMLAttributes<HTMLElement>>>()
    component {
        ref = fRef
        +props.except("component", "direction")
        css(props.className) {
            display = Display.flex
            flexDirection = props.direction ?: FlexDirection.column
        }
    }
}