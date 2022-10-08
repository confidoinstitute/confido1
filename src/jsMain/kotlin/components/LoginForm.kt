package components

import csstype.*
import emotion.react.css
import mui.material.*
import mui.system.sx
import payloads.requests.Login
import react.*
import react.dom.html.ReactHTML.div
import react.dom.onChange
import react.dom.html.InputType
import utils.eventValue

val LoginForm = FC<Props> {
    val appState = useContext(AppStateContext)
    var email by useState<String>("")
    var password by useState<String>("")

    Paper {
        sx {
            marginTop = 10.px
            padding = 10.px
        }
        div {
            div {
                TextField {
                    variant = FormControlVariant.standard
                    id = "email-field"
                    label = ReactNode("Email")
                    value = email
                    disabled = appState.stale
                    onChange = {
                        email = it.eventValue()
                    }
                }
            }
            div {
                TextField {
                    variant = FormControlVariant.standard
                    id = "password-field"
                    type = InputType.password
                    label = ReactNode("Password")
                    value = password
                    disabled = appState.stale
                    onChange = {
                        password = it.eventValue()
                    }
                }
            }
            div {
                css {
                    marginTop = 5.px
                }
                Button {
                    onClick = {
                        // TODO: Handle failure
                        Client.postData("/login", Login(email, password))
                    }
                    disabled = appState.stale
                    +"Log in"
                }
            }
        }
    }
}
