package components.redesign.forms

import csstype.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.form

external interface FormProps : PropsWithChildren, PropsWithClassName {
    var onSubmit: () -> Unit?
}

val Form = FC<FormProps> { props ->
    form {
        onSubmit = {
            props.onSubmit?.invoke()
            it.preventDefault()
        }

        +props.children
    }
}
