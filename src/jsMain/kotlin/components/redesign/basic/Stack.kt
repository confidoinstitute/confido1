package components.redesign.basic

import csstype.*
import emotion.react.*
import org.w3c.dom.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div


external interface StackProps : PropsWithChildren, PropsWithClassName {
    var component: ElementType<*>?
    var direction: FlexDirection?
}

val Stack = FC<StackProps> { props ->
    val component: ElementType<HTMLAttributes<HTMLElement>> = (props.component ?: div).asDynamic()
    component {
        css(props.className) {
            display = Display.flex
            flexDirection = props.direction ?: FlexDirection.column
        }
        +props.children
    }
}