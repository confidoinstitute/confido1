package components.redesign.forms

import dom.html.HTMLFormElement
import react.*
import react.dom.html.FormHTMLAttributes
import react.dom.html.ReactHTML.form

val Form = ForwardRef<HTMLFormElement, FormHTMLAttributes<HTMLFormElement>> { props, fRef ->
    form {
        +props
        onSubmit = {
            props.onSubmit?.invoke(it)
            it.preventDefault()
        }
    }
}
