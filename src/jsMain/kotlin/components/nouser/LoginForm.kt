package components.nouser

import components.AppStateContext
import emotion.react.css
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import payloads.requests.PasswordLogin
import payloads.requests.SendMailLink
import react.*
import react.dom.html.ReactHTML.div
import react.dom.onChange
import react.dom.html.InputType
import utils.eventValue
import utils.themed

val LoginForm = FC<Props> {
    val (_, stale) = useContext(AppStateContext)
    var email by useState<String>("")
    var password by useState<String>("")

    Paper {
        sx {
            marginTop = themed(2)
            padding = themed(2)
        }
        div {
            Typography {
                variant = TypographyVariant.h6
                +"Login with password"
            }
            div {
                TextField {
                    variant = FormControlVariant.standard
                    id = "email-field"
                    label = ReactNode("Email")
                    value = email
                    disabled = stale
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
                    disabled = stale
                    onChange = {
                        password = it.eventValue()
                    }
                }
            }
            div {
                css {
                    marginTop = themed(2)
                }
                Button {
                    onClick = {
                        // TODO: Handle failure
                        Client.postData("/login", PasswordLogin(email, password))
                    }
                    disabled = stale
                    +"Log in"
                }
            }
        }
    }

    Paper {
        sx {
            marginTop = themed(2)
            padding = themed(2)
        }
        div {
            Typography {
                variant = TypographyVariant.h6
                +"Login by magic link"
            }
            div {
                TextField {
                    variant = FormControlVariant.standard
                    id = "email-field"
                    label = ReactNode("Email")
                    value = email
                    disabled = stale
                    onChange = {
                        email = it.eventValue()
                    }
                }
            }
            div {
                css {
                    marginTop = themed(2)
                }
                Button {
                    onClick = {
                        // TODO: Handle failure
                        Client.postData("/login_email/create", SendMailLink(email, "/"))
                    }
                    disabled = stale
                    +"Send magic link"
                }
            }
        }
    }
}
