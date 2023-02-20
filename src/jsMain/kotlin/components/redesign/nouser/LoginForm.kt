package components.redesign.nouser

import components.LoginContext
import components.redesign.basic.MainPalette
import components.redesign.basic.Stack
import components.redesign.forms.Form
import components.redesign.forms.TextInput
import components.showError
import csstype.*
import emotion.react.css
import hooks.useCoroutineLock
import io.ktor.http.*
import payloads.requests.PasswordLogin
import payloads.requests.SendMailLink
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import utils.*

val loginLinkClass = emotion.css.ClassName {
    marginTop = 12.px
    color = Color("#DDDDDD80")
    background = None.none
    border = None.none
    fontSize = 15.px
    lineHeight = 18.px
    fontFamily = FontFamily.sansSerif
    cursor = Cursor.pointer

    hover {
        textDecoration = TextDecoration.underline
    }
}

val loginInputClass = emotion.css.ClassName {
    padding = Padding(10.px, 12.px)
    backgroundColor = Color("#96FFFF33")
    color = Color("#FFFFFF")
    borderWidth = 0.px
    borderRadius = 0.px
    width = 100.pct

    fontSize = 17.px
    lineHeight = 20.px
    fontFamily = FontFamily.sansSerif
    resize = None.none

    firstChild {
        borderTopLeftRadius = 10.px
        borderTopRightRadius = 10.px
    }
    lastChild {
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

    val loginState = useContext(LoginContext)
    var email by useState<String>(props.prefilledEmail ?: "")
    var password by useState<String>("")

    var mode by useState(LoginMode.MagicLink)
    var emailSent by useState(false)
    var emailError by useState<String?>(null)
    var passwordError by useState<String?>(null)

    val login = useCoroutineLock()

    fun attemptLogin() {
        val trimmedEmail = email.trim()
        val valid = isEmailValid(trimmedEmail)
        if (!valid) {
            emailError = "This email you used is not valid."
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
            padding = Padding(0.px, 15.px)
            flexDirection = FlexDirection.column
            alignItems = AlignItems.stretch
            textAlign = TextAlign.center
            fontFamily = FontFamily.sansSerif
        }

        Form {
            onSubmit = ::attemptLogin
            Stack {
                direction = FlexDirection.column
                css {
                    gap = 1.px
                }
                if (!emailSent) {
                    TextInput {
                        className = loginInputClass
                        placeholder = "Email"
                        value = email
                        onChange = {
                            email = it.target.value
                        }
                    }

                    if (mode == LoginMode.Password) {
                        TextInput {
                            className = loginInputClass
                            placeholder = "Password"
                            type = InputType.password
                            value = password
                            //error = passwordError != null  // TODO(Prin) Can I delete this?
                            onChange = {
                                password = it.target.value
                            }
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
                        marginTop = 12.px
                        width = 100.pct
                        borderRadius = 10.px
                    }
                    type = ButtonType.submit
                    this.palette = MainPalette.default
                    disabled = emailSent || login.running
                    +"Log in"
                }
            }

            if (emailError != null || passwordError != null) {
                div {
                    css {
                        marginTop = 20.px
                        marginBottom = 5.px
                        padding = Padding(10.px, 12.px)
                        textAlign = TextAlign.center
                        backgroundColor = Color("#a327d7")  // TODO(Prin): Use palette.
                        color = Color("#FFFFFF")
                        borderWidth = 0.px
                        borderRadius = 5.px
                        width = 100.pct

                        fontSize = 15.px
                        lineHeight = 18.px
                        fontFamily = FontFamily.sansSerif
                    }
                    +(emailError ?: passwordError ?: "")
                }
            }
        }

        if (mode == LoginMode.MagicLink) {
            if (!emailSent) {
                button {
                    className = loginLinkClass
                    onClick = {
                        mode = LoginMode.Password
                        emailError = null
                    }
                    +"Log in with a password"
                }
            } else {
                button {
                    className = loginLinkClass
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
            button {
                className = loginLinkClass
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