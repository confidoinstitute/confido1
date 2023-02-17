package components.redesign.nouser

import components.LoginContext
import components.UserAvatar
import components.redesign.basic.MainPalette
import components.redesign.forms.LoginTextInput
import components.showError
import components.userListItemText
import csstype.*
import dom.html.HTMLLIElement
import emotion.react.css
import hooks.useCoroutineLock
import icons.smallLogo
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.js.Object
import kotlinx.js.ReadonlyArray
import kotlinx.js.jso
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import payloads.requests.PasswordLogin
import payloads.requests.SendMailLink
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.button
import react.dom.onChange
import react.dom.html.ReactHTML.span
import tools.confido.refs.ref
import users.User
import utils.*

enum class LoginMode {
    MagicLink,
    Password,
}

external interface LoginFormProps : Props {
    var prefilledEmail: String?
}

val LoginFormRedesigned = FC<LoginFormProps> { props ->
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
                            passwordError = "Wrong password or email, please try again."
                            password = ""
                        }
                    }) {
                        loginState.login()
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
            // TODO(Prin): Use a palette.
            background = Color("#6733DA")
        }

        // TODO(Prin): Replace with SVG.
        span {
            css {
                display = Display.flex
            }

            smallLogo {}
            span {
                css {
                    fontFamily = FontFamily.serif
                    fontWeight = 600.unsafeCast<FontWeight>()
                    fontSize = 24.px
                    lineHeight = 29.px
                    color = Color("#FFFFFF")
                    marginLeft = 10.px
                    // TODO(Prin): Better vertical center alignment!
                    paddingTop = 14.px
                }
                +"Confido"
            }
        }

        if (!emailSent) {
            LoginTextInput {
                placeholder = "Email"
                id = "email-field"
                /*
                helperText = if (emailError != null) {
                    ReactNode(emailError!!)
                } else {
                    when (mode) {
                        LoginMode.MagicLink -> ReactNode("We will send a login link to you.")
                        LoginMode.Password -> ReactNode("")
                    }
                }
                 */
                value = email
                onChange = {
                    email = it.target.value  // TODO(Prin): Is this correct?
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

        components.redesign.forms.Button {
            css {
                width = 100.pct
                borderRadius = 10.px
            }
            palette = MainPalette.inverted
            disabled = emailSent || login.running
            onClick = { attemptLogin() }
            +"Log in"
        }

        if (emailError != null) {
            span {
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
                +emailError!!
            }
        }

        Box {
            sx {
                marginTop = themed(1)
                textAlign = TextAlign.center
            }
            if (mode == LoginMode.MagicLink) {
                if (!emailSent) {
                    Link {
                        sx {
                            color = Color("#DDDDDD80")
                            fontSize = 15.px
                            fontFamily = FontFamily.sansSerif
                        }
                        component = button
                        variant = TypographyVariant.body2
                        onClick = {
                            mode = LoginMode.Password
                            emailError = null
                        }
                        +"Log in with a password"
                    }
                } else {
                    Link {
                        component = button
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
                    component = button
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
        +userListItemText(option, withInactive = true)
    }

external interface LoginByUserSelectFormProps : Props {
    var helperText: String?
    var demoMode: Boolean
}

val LoginByUserSelectInner = FC<LoginByUserSelectFormProps> { props->
    val loginState = useContext(LoginContext)
    var chosenUser by useState<User?>(null)
    var users by useState<ReadonlyArray<User>?>(null)
    var open by useState(false)
    val loading = open && users == null

    val login = useCoroutineLock()

    useEffect(loading) {
        if (!loading) {
            return@useEffect
        }

        runCoroutine {
            Client.send("/login_users", HttpMethod.Get, onError = {showError?.invoke(it)}) {
                val availableUsers: ReadonlyArray<User> = body()
                // Required for the autocomplete groupBy
                availableUsers.sortBy { it.type }
                users = availableUsers
            }
        }
    }

    useEffect(open) {
        if (!open) {
            users = null
        }
    }
    val autocomplete: FC<AutocompleteProps<User>> = Autocomplete
    fun attemptLogin() = login {
        chosenUser?.let {
            Client.sendData("/login_users", it.ref, onError = { showError?.invoke(it) }) {
                loginState.login()
            }
        }
    }
    autocomplete {
        options = users ?: emptyArray()
        renderInput = { params ->
            TextField.create {
                Object.assign(this, params)
                margin = FormControlMargin.normal
                placeholder = "User name or e-mail"
                label = ReactNode("Choose account to see Confido from their view")
                helperText = props.helperText?.let { ReactNode(it) }
            }
        }
        getOptionDisabled = { option -> !option.active }
        renderOption = ::renderOption
        autoComplete = true
        getOptionLabel = ::getOptionLabel
        groupBy = ::groupBy
        ListboxComponent = List
        ListboxProps = jso<ListProps> {
            dense = true
        }
        onChange = { _, value: User, _, _ -> chosenUser = value }
        fullWidth = true
        this.loading = loading
        this.open = open
        onOpen = { open = true }
        onClose = { _, _ -> open = false }
    }

    Button {
        variant = ButtonVariant.contained
        fullWidth = true
        disabled = chosenUser == null || login.running
        onClick = { attemptLogin() }
        +"Log in"
    }
}

val LoginByUserSelectForm = FC<LoginByUserSelectFormProps> { props ->
    Container {
        maxWidth = byTheme("xs")
        sx {
            padding = themed(2)
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
        }

        LoginByUserSelectInner {+props}
    }
}
