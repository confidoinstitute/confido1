package components.nouser

import components.AppStateContext
import csstype.*
import emotion.react.css
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import payloads.requests.PasswordLogin
import payloads.requests.SendMailLink
import react.*
import react.dom.onChange
import react.dom.html.InputType
import react.dom.html.ReactHTML.h1
import utils.byTheme
import utils.eventValue
import utils.themed

enum class LoginMode {
    MagicLink,
    Password,
}

val LoginForm = FC<Props> {
    val (_, stale) = useContext(AppStateContext)
    var email by useState<String>("")
    var password by useState<String>("")

    var mode by useState(LoginMode.MagicLink)
    var emailSent by useState(false)

    fun attemptLogin() {
        when (mode) {
            LoginMode.MagicLink -> {
                Client.postData("/login_email/create", SendMailLink(email, "/"))
                emailSent = true
            }

            LoginMode.Password -> Client.postData("/login", PasswordLogin(email, password))
        }
    }

    Container {
        maxWidth = byTheme("xs")
        sx {
            marginTop = themed(2)
            padding = themed(2)
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
        }

        Typography {
            variant = TypographyVariant.h5
            component = h1
            +"Log in"
        }

        if (!emailSent) {
            TextField {
                sx {
                    marginTop = themed(2)
                }
                margin = FormControlMargin.normal
                variant = FormControlVariant.outlined
                fullWidth = true
                id = "email-field"
                label = ReactNode("Email")
                helperText = when (mode) {
                    LoginMode.MagicLink -> ReactNode("We will send a login link to you.")
                    LoginMode.Password -> ReactNode("")
                }
                value = email
                disabled = stale
                onChange = {
                    email = it.eventValue()
                }
                onKeyUp = {
                    if (it.key == "Enter") {
                        attemptLogin()
                    }
                }
            }

            if (mode == LoginMode.Password) {
                TextField {
                    margin = FormControlMargin.normal
                    variant = FormControlVariant.outlined
                    fullWidth = true
                    id = "password-field"
                    type = InputType.password
                    label = ReactNode("Password")
                    value = password
                    disabled = stale
                    onChange = {
                        password = it.eventValue()
                    }
                    onKeyUp = {
                        if (it.key == "Enter") {
                            attemptLogin()
                        }
                    }
                }
            }
        } else {
            Paper {
                sx {
                    width = 100.pct
                    margin = themed(2)
                    padding = themed(2)
                }
                // TODO: Email icon?
                Typography {
                    sx {
                        textAlign = TextAlign.center
                    }
                    variant = TypographyVariant.h5
                    +"Confirm your email"
                }
                Typography {
                    sx {
                        textAlign = TextAlign.center
                        marginTop = themed(2)
                    }
                    variant = TypographyVariant.body1
                    +"We have sent an email with a login link to"
                }
                Typography {
                    sx {
                        textAlign = TextAlign.center
                    }
                    variant = TypographyVariant.body1
                    +email
                }
            }
        }

        Button {
            variant = ButtonVariant.contained
            fullWidth = true
            disabled = stale || emailSent
            onClick = { attemptLogin() }
            +"Log in"
        }

        if (mode == LoginMode.MagicLink) {
            if (!emailSent) {
                Box {
                    sx {
                        marginTop = themed(1)
                        textAlign = TextAlign.center
                    }
                    Link {
                        href = "#"
                        variant = TypographyVariant.body2
                        onClick = {
                            mode = LoginMode.Password
                        }
                        +"Log in with a password"
                    }
                }
            } else {
                Box {
                    sx {
                        marginTop = themed(1)
                        textAlign = TextAlign.center
                    }
                    Link {
                        href = "#"
                        variant = TypographyVariant.body2
                        onClick = {
                            emailSent = false
                            password = ""
                            email = ""
                        }
                        +"Retry"
                    }
                }
            }
        }
    }
}
