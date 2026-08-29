package tools.confido.web.components.redesign.forms

import web.html.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.form

external interface FormProps : FormHTMLAttributes<HTMLFormElement>, PropsWithRef<HTMLFormElement>

val Form = FC<FormProps> { props->
    form {
        +props
        onSubmit = {
            props.onSubmit?.invoke(it)
            it.preventDefault()
        }
    }
}

/**
 * Uniformly compacts the form controls inside, for tight surfaces like the
 * floating presenter panel: CSS zoom scales every descendant — current and
 * future components alike — so nothing needs a per-component compact variant.
 * The content also lays out proportionally wider (1/zoom of the container),
 * so rows gain breathing room, not just smaller text.
 */
val CompactFormStyling = FC<PropsWithChildren> { props ->
    ReactHTML.div {
        style = js.objects.unsafeJso { asDynamic().zoom = 0.8 }
        +props.children
    }
}
