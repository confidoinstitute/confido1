package components.redesign.forms

import dom.html.*
import react.*
import react.dom.html.*
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
