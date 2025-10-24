package components.redesign.forms

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
