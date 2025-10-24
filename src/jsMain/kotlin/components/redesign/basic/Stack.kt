package components.redesign.basic

import web.cssom.*
import emotion.react.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import utils.*
import web.html.HTMLElement


external interface StackProps : HTMLAttributes<HTMLElement>, PropsWithRef<HTMLElement> {
    var component: ElementType<*>?
    var direction: FlexDirection?
}

val Stack = FC<StackProps> { props->
    val component: ElementType<HTMLAttributes<HTMLElement>> = (props.component ?: div).unsafeCast<ElementType<HTMLAttributes<HTMLElement>>>()
    component {
        +props.except("component", "direction")
        css(props.className) {
            display = Display.flex
            flexDirection = props.direction ?: FlexDirection.column
        }
    }
}