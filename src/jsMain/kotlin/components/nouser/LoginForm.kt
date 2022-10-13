package components.nouser

import components.AppStateContext
import csstype.*
import io.ktor.http.*
import kotlinx.coroutines.*
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
import utils.postJson
import utils.themed
import kotlin.coroutines.EmptyCoroutineContext

enum class LoginMode {
    MagicLink,
    Password,
}

external interface LoginFormProps : Props {
    var prefilledEmail: String
}

val LoginForm = FC<LoginFormProps> { props ->
    val (_, stale) = useContext(AppStateContext)
    var email by useState<String>(props.prefilledEmail)
    var password by useState<String>("")

    var mode by useState(LoginMode.MagicLink)
    var emailSent by useState(false)
    var wrongPassword by useState(false)

    fun attemptLogin() {
        when (mode) {
            LoginMode.MagicLink -> {
                Client.postData("/login_email/create", SendMailLink(email, "/"))
                emailSent = true
            }

            LoginMode.Password -> {
                CoroutineScope(EmptyCoroutineContext).launch {
                    val response = Client.httpClient.postJson("/login", PasswordLogin(email, password)) {}
                    console.log(response)
                    if (response.status == HttpStatusCode.Unauthorized) {
                        wrongPassword = true
                        password = ""
                    }
                }
            }
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
                    helperText = if (wrongPassword) {
                        ReactNode("Wrong password or email, please try again.")
                    } else {
                        null
                    }
                    error = wrongPassword
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

        Box {
            sx {
                marginTop = themed(1)
                textAlign = TextAlign.center
            }
            if (mode == LoginMode.MagicLink) {
                if (!emailSent) {
                    Link {
                        href = "#"
                        variant = TypographyVariant.body2
                        onClick = {
                            mode = LoginMode.Password
                        }
                        +"Log in with a password"
                    }
                } else {
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
            } else {
                Link {
                    href = "#"
                    variant = TypographyVariant.body2
                    onClick = {
                        mode = LoginMode.MagicLink
                        password = ""
                        wrongPassword = false
                    }
                    +"Log in with email only"
                }
            }
        }
    }
}
