package components.redesign.nouser

import Client
import components.*
import components.redesign.basic.*
import components.redesign.forms.*
import csstype.*
import dom.html.*
import emotion.react.*
import hooks.*
import io.ktor.http.*
import payloads.requests.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import utils.*

val LoginLinkButton = ButtonBase.withStyle {
    marginTop = 12.px
    color = Color("#DDDDDD80")
    background = None.none
    border = None.none
    fontSize = 15.px
    lineHeight = 18.px
    fontFamily = sansSerif
    cursor = Cursor.pointer

    hover {
        textDecoration = TextDecoration.underline
    }
}

external interface LoginInputProps : InputHTMLAttributes<HTMLInputElement> {
    var error: Boolean
}

val LoginInput = input.withStyle<LoginInputProps>("error") {props ->
    padding = Padding(12.px, 12.px)
    backgroundColor = Color("#96FFFF33")
    color = Color("#FFFFFF")
    border = Border(1.px, LineStyle.solid, if (props.error) Color("#F35454") else Color("transparent"))
    borderRadius = 0.px
    width = 100.pct

    fontSize = 17.px
    lineHeight = 20.px
    fontFamily = sansSerif

    firstOfType {
        borderTopLeftRadius = 10.px
        borderTopRightRadius = 10.px
    }
    lastOfType {
        borderBottomLeftRadius = 10.px
        borderBottomRightRadius = 10.px
    }

    focus {
        outline = Outline(2.px, LineStyle.solid, MainPalette.primary.color)
    }

    placeholder {
        color = Color("#FFFFFF80")
    }
}

enum class LoginMode {
    MagicLink,
    Password,
}

external interface LoginFormProps : Props {
    var prefilledEmail: String?
}

val LoginForm = FC<LoginFormProps> { props ->
    val palette = MainPalette.login

    useDocumentTitle("Log In")

    val loginState = useContext(LoginContext)
    var email by useState(props.prefilledEmail ?: "")
    var password by useState("")

    var mode by useState(LoginMode.MagicLink)
    var emailSent by useState(false)
    var emailError by useState<String?>(null)
    var passwordError by useState<String?>(null)

    val login = useCoroutineLock()

    fun attemptLogin() {
        val trimmedEmail = email.trim()
        val valid = isEmailValid(trimmedEmail)
        if (!valid) {
            emailError = if (trimmedEmail.isBlank()) "Please enter an email address." else "The email address is not valid."
            return
        }
        emailError = null

        login {
            when (mode) {
                LoginMode.MagicLink -> {
                    Client.sendData("/login_email/create", SendMailLink(trimmedEmail, "/"), onError = {showError?.invoke(it)}) {
                        emailSent = true
                    }
                }

                LoginMode.Password -> {
                    Client.sendData("/login", PasswordLogin(trimmedEmail, password), onError = {
                        if (status == HttpStatusCode.Unauthorized) {
                            passwordError = "Wrong password or email. Please try again."
                            password = ""
                        }
                    }) {
                        loginState.login()
                    }
                }
            }
        }
    }

    Stack {
        css {
            width = 100.pct
            maxWidth = 400.px
            margin = Margin(15.px, 0.px)
            padding = Padding(0.px, 15.px)
            flexDirection = FlexDirection.column
            alignItems = AlignItems.stretch
            textAlign = TextAlign.center
            fontFamily = sansSerif
        }

        Form {
            onSubmit = {attemptLogin()}
            Stack {
                direction = FlexDirection.column
                css {
                    gap = 1.px
                }
                if (!emailSent) {
                    LoginInput {
                        error = emailError != null
                        placeholder = "Email"
                        value = email
                        onChange = {
                            email = it.target.value
                        }
                    }

                    if (mode == LoginMode.Password) {
                        LoginInput {
                            error = passwordError != null
                            placeholder = "Password"
                            type = InputType.password
                            value = password
                            onChange = {
                                password = it.target.value
                            }
                        }
                    }

                    if (emailError != null || passwordError != null) {
                        p {
                            css {
                                marginTop = 6.px
                                color = Color("#F35454")
                                width = 100.pct

                                fontSize = 12.px
                                lineHeight = 15.px
                                fontWeight = integer(400)
                                fontFamily = sansSerif
                            }
                            +(emailError ?: passwordError ?: "")
                        }
                    }
                } else {
                    Stack {
                        css {
                            fontSize = 14.px
                            lineHeight = 17.px
                            textAlign = TextAlign.center
                            margin = Margin(10.px, 15.px)
                            color = palette.text.color
                        }
                        span {
                            +"We have sent an email with a login link to"
                        }
                        b {
                            +email
                        }
                        span {
                            css {
                                marginTop = 10.px
                                fontSize = 12.px
                            }
                            +"Please check the spam folder in case you didn't find the email."
                        }
                    }
                }
            }

            // Changed the design to hide the button (rather than just disable it) when the email was sent.
            if (!emailSent) {
                components.redesign.forms.Button {
                    css {
                        marginTop = 14.px
                        width = 100.pct
                        borderRadius = 10.px
                    }
                    type = ButtonType.submit
                    this.palette = MainPalette.default
                    disabled = emailSent || login.running
                    +"Log in"
                }
            }
        }

        if (mode == LoginMode.MagicLink) {
            if (!emailSent) {
                LoginLinkButton {
                    onClick = {
                        mode = LoginMode.Password
                        emailError = null
                    }
                    +"Log in with a password"
                }
            } else {
                LoginLinkButton {
                    onClick = {
                        emailSent = false
                        password = ""
                        email = ""
                        emailError = null
                        passwordError = null
                    }
                    +"Retry"
                }
            }
        } else {
            LoginLinkButton {
                onClick = {
                    mode = LoginMode.MagicLink
                    password = ""
                    passwordError = null
                    emailError = null
                }
                +"Log in with email only"
            }
        }
    }
}
