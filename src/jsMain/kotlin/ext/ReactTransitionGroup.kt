@file:JsModule("react-transition-group")
@file:JsNonModule

@file:Suppress(
    "NAME_CONTAINS_ILLEGAL_CHARS",
    "NESTED_CLASS_IN_EXTERNAL_INTERFACE",
)
package ext.reacttransitiongroup

import cssom.CSS
import dom.html.HTMLElement
import react.*

external interface TransitionProps : PropsWithChildren {
    var nodeRef: RefObject<dynamic>?
    var `in`: Boolean
    var mountOnEnter: Boolean
    var unmountOnExit: Boolean
    var appear: Boolean
    var enter: Boolean
    var exit: Boolean
    var timeout: Number
    var addEndListener: (dynamic, dynamic) -> Unit
    var onEnter: (dynamic, dynamic) -> Unit
    var onEntering: (dynamic, dynamic) -> Unit
    var onEntered: (dynamic, dynamic) -> Unit
    var onExit: (dynamic, dynamic) -> Unit
    var onExiting: (dynamic, dynamic) -> Unit
    var onExited: (dynamic, dynamic) -> Unit
}

external interface CSSTransitionProps : TransitionProps, PropsWithClassName {
    var classNames: String
}

// language=JavaScript
@JsName("""(/*union*/{outIn: 'out-in', inOut: 'in-out'}/*union*/)""")
sealed external interface SwitchTransitionMode {
    companion object {
        val outIn: SwitchTransitionMode
        val inOut: SwitchTransitionMode
    }
}

external interface SwitchTransitionProps : PropsWithChildren {
    var mode: SwitchTransitionMode
}

external interface TransitionGroupProps : PropsWithChildren {
    var component: dynamic
    var appear: Boolean
    var enter: Boolean
    var exit: Boolean
    var childFactory: (ReactElement<*>) -> ReactElement<*>
}

external val Transition: ComponentType<TransitionProps>
external val CSSTransition: ComponentType<CSSTransitionProps>
external val SwitchTransition: ComponentType<SwitchTransitionProps>
external val TransitionGroup: ComponentType<TransitionGroupProps>