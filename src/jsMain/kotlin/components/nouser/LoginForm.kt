package components.nouser

import components.AppStateContext
import components.UserAvatar
import components.userListItemText
import csstype.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.js.Object
import kotlinx.js.ReadonlyArray
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import org.w3c.dom.*
import payloads.requests.PasswordLogin
import payloads.requests.SendMailLink
import react.*
import react.dom.html.*
import react.dom.onChange
import react.dom.html.ReactHTML.h1
import tools.confido.refs.ref
import users.User
import utils.*
import kotlin.coroutines.EmptyCoroutineContext

enum class LoginMode {
    MagicLink,
    Password,
}

external interface LoginFormProps : Props {
    var prefilledEmail: String?
}

val LoginForm = FC<LoginFormProps> { props ->
    val (_, stale) = useContext(AppStateContext)
    var email by useState<String>(props.prefilledEmail ?: "")
    var password by useState<String>("")

    var mode by useState(LoginMode.MagicLink)
    var emailSent by useState(false)
    var emailError by useState<String?>(null)
    var passwordError by useState<String?>(null)

    fun attemptLogin() {
        val trimmedEmail = email.trim()
        val valid = isEmailValid(trimmedEmail)
        if (!valid) {
            emailError = "This email address is not valid."
            return
        }

        when (mode) {
            LoginMode.MagicLink -> {
                Client.postData("/login_email/create", SendMailLink(trimmedEmail, "/"))
                emailSent = true
            }

            LoginMode.Password -> {
                CoroutineScope(EmptyCoroutineContext).launch {
                    val response = Client.httpClient.postJson("/login", PasswordLogin(trimmedEmail, password)) {}
                    console.log(response)
                    if (response.status == HttpStatusCode.Unauthorized) {
                        passwordError = "Wrong password or email, please try again."
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
                helperText = if (emailError != null) {
                    ReactNode(emailError!!)
                } else {
                    when (mode) {
                        LoginMode.MagicLink -> ReactNode("We will send a login link to you.")
                        LoginMode.Password -> ReactNode("")
                    }
                }
                error = emailError != null
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
                    helperText = if (passwordError != null) {
                        ReactNode(passwordError!!)
                    } else {
                        null
                    }
                    error = passwordError != null
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
                Typography {
                    sx {
                        textAlign = TextAlign.center
                        marginTop = themed(2)
                        fontSize = FontSize.smaller
                        color = Color("#999")
                    }
                    variant = TypographyVariant.body2
                    +"Please check your spam folder in case you didn't get the email."
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
                            emailError = null
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
                            emailError = null
                            passwordError = null
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
                        passwordError = null
                        emailError = null
                    }
                    +"Log in with email only"
                }
            }
        }
    }
}


internal fun renderInput(params: AutocompleteRenderInputParams) =
    TextField.create {
        kotlinx.js.Object.assign(this, params)
        margin = FormControlMargin.normal
        placeholder = "User name or e-mail"
        label = ReactNode("Choose an account")
    }

internal fun groupBy(u: User) = if (u.type.isProper()) "Organization users" else "Guests"

internal fun getOptionLabel(option: User) = option.nick ?: option.email ?: "Temporary guest"

internal fun renderOption(
    attributes: HTMLAttributes<HTMLLIElement>,
    option: User,
    state: AutocompleteRenderOptionState
) =
    ListItem.create {
        Object.assign(this, attributes)
        ListItemAvatar {
            UserAvatar {
                user = option
            }
        }
        +userListItemText(option)
    }

val LoginByUserSelectForm = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    var chosenUser by useState<User?>(null)
    var users by useState<ReadonlyArray<User>?>(null)

    useEffectOnce {
        CoroutineScope(EmptyCoroutineContext).launch {
            val availableUsers: ReadonlyArray<User> = Client.httpClient.getJson("/login_users") {}.body()
            users = availableUsers
        }
    }

    fun attemptLogin() {
        chosenUser?.let {
            Client.postData("/login_users", it.ref)
        }
    }


    Container {
        maxWidth = byTheme("xs")
        sx {
            padding = themed(2)
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
        }

        val autocomplete: FC<AutocompleteProps<User>> = Autocomplete
        autocomplete {
            options = users ?: emptyArray()
            renderInput = ::renderInput
            renderOption = ::renderOption
            autoComplete = true
            getOptionLabel = ::getOptionLabel
            groupBy = ::groupBy
            ListboxComponent = List
            ListboxProps = utils.jsObject {
                dense = true
            }.unsafeCast<ListProps>()
            onChange = { _, value: User, _, _ -> chosenUser = value }
            fullWidth = true
        }

        Button {
            variant = ButtonVariant.contained
            fullWidth = true
            disabled = stale || chosenUser == null
            onClick = { attemptLogin() }
            +"Log in"
        }
    }
}