package components.profile

import components.AlertSnackbar
import components.AppStateContext
import components.showError
import hooks.useCoroutineLock
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.*
import react.dom.onChange
import payloads.requests.SetNick
import payloads.requests.SetPassword
import payloads.requests.StartEmailVerification
import react.dom.html.ButtonType
import react.dom.html.InputType
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.form
import users.*
import utils.*
import kotlin.coroutines.EmptyCoroutineContext

external interface UserSettingsCardProps : Props {
    var disabled: Boolean
    var user: User
    var onSuccess: (String) -> Unit
    var onError: (String) -> Unit
}

val UserSettingsName = FC<UserSettingsCardProps> { props ->
    var name by useState(props.user.nick ?: "")

    val change = useCoroutineLock()

    fun changeName() = change {
        Client.sendData("/profile/nick", SetNick(name), onError = {props.onError("Could not update name.")}) {
            props.onSuccess("Name updated.")
        }
    }

    Card {
        form {
            onSubmit = { it.preventDefault(); changeName() }
            CardHeader {
                title = ReactNode("Profile")
            }
            CardContent {
                Stack {
                    TextField {
                        variant = FormControlVariant.outlined
                        id = "name-field"
                        label = ReactNode("Name")
                        helperText =
                            ReactNode("Your name may appear whenever you comment or make predictions. You can change it at any time.")
                        value = name
                        onChange = {
                            name = it.eventValue()
                        }
                    }
                }
            }
            CardActions {
                sx {
                    padding = themed(2)
                }
                Button {
                    type = ButtonType.submit
                    variant = ButtonVariant.contained
                    disabled = props.disabled || change.running

                    if (props.user.nick == null) {
                        +"Set name"
                    } else {
                        +"Update name"
                    }
                }
            }
        }
    }
}

val UserSettingsEmail = FC<UserSettingsCardProps> { props ->
    val user = props.user
    var email by useState(user.email ?: "")
    var emailError by useState<String?>(null)

    // Note that this is currently only stored on the frontend.
    // Navigating away from user settings and coming back will show the user the old email
    // without any verification prompt.
    var pendingEmailChange by useState<String?>(null)

    val change = useCoroutineLock()

    fun changeEmail() {
        val changed = (user.email ?: "") != email
        if (!changed) {
            emailError = "This is already your current email address."
            return
        }
        if (!isEmailValid(email)) {
            emailError = "This email address is not valid."
            return
        }

        change {
            Client.sendData("/profile/email/start_verification",StartEmailVerification(email), onError = {emailError = it}) {
                emailError = null
                pendingEmailChange = email
                props.onSuccess("We sent you a verification e-mail.")
            }
        }
    }

    if (user.type == UserType.GUEST && user.email == null && pendingEmailChange == null) {
        Alert {
            AlertTitle {
                +"No email is set!"
            }
            severity = AlertColor.error
            +"It is impossible to log back in without having an email set. Please set an email below if you want to return later."
        }
    } else {
        if (!user.emailVerified || pendingEmailChange != null) {
            Alert {
                AlertTitle {
                    +"Your email is not verified!"
                }
                severity = AlertColor.warning
                if (pendingEmailChange != null) {
                    +"We have sent you a verification email, please check your inbox."
                } else {
                    +"To resolve this, we need to send you a verification email."
                }
                br {}
                Box {
                    sx {
                        marginTop = themed(1)
                    }
                    Button {
                        disabled = props.disabled
                        onClick = {changeEmail()}

                        if (pendingEmailChange != null) {
                            +"Resend verification email"
                        } else {
                            +"Send verification email"
                        }
                    }
                }
            }
        }
    }

    Card {
        form {
            onSubmit = { it.preventDefault(); changeEmail() }
            CardHeader {
                title = ReactNode("Email address")
            }

            CardContent {
                // TODO: Better explanation of email visibility
                Stack {
                    TextField {
                        variant = FormControlVariant.outlined
                        id = "email-field"
                        label = ReactNode("Email address")
                        helperText = emailError?.let { ReactNode(it) }
                            ?: ReactNode("This email address is used for logging into your account and may be shown to other members of the organization.")
                        error = emailError != null
                        required = true
                        value = email
                        onChange = {
                            email = it.eventValue()
                        }
                    }
                }
            }

            CardActions {
                sx {
                    padding = themed(2)
                }
                Button {
                    type = ButtonType.submit
                    variant = ButtonVariant.contained
                    disabled = props.disabled || change.running
                    +"Update email"
                }
            }
        }
    }
}

val UserSettingsPassword = FC<UserSettingsCardProps> {props ->
    val (appState, _) = useContext(AppStateContext)

    var currentPassword by useState("")
    var newPassword by useState("")
    var newPasswordRepeat by useState("")

    var currentPasswordError by useState<String?>(null)
    var newPasswordError by useState<String?>(null)
    var newPasswordRepeatError by useState<String?>(null)

    val change = useCoroutineLock()

    fun changePassword() {
        currentPasswordError = null
        newPasswordError = null
        newPasswordRepeatError = null

        if (newPassword != newPasswordRepeat) {
            newPasswordRepeatError = "The passwords do not match."
            return
        }

        when (checkPassword(newPassword)) {
            PasswordCheckResult.OK -> {}
            PasswordCheckResult.TOO_SHORT -> {
                newPasswordError = "Please choose a password at least $MIN_PASSWORD_LENGTH characters long."
                return
            }
            PasswordCheckResult.TOO_LONG -> {
                newPasswordError = "Please choose a password at most $MAX_PASSWORD_LENGTH characters long."
                return
            }
        }

        val setPassword = SetPassword(if (appState.myPasswordIsSet) currentPassword else null, newPassword)

        change {
            Client.sendData("/profile/password", setPassword, onError = {
                if (status == HttpStatusCode.Unauthorized) {
                    currentPasswordError = "The current password is incorrect."
                }
            }) {
                props.onSuccess("The password was changed.")
            }
        }
    }

    fun resetPassword() = change {
            Client.send("/profile/password/reset", onError = {
                props.onError("We could not reset your password. Please try again later.")
            }) {
                props.onSuccess("An e-mail to reset your password was sent.")
            }
            currentPassword = ""
            newPassword = ""
            newPasswordRepeat = ""
        }

    Card {
        form {
            onSubmit = { it.preventDefault(); changePassword(); }
            CardHeader {
                title = ReactNode("Password")
            }

            CardContent {
                Stack {
                    if (appState.myPasswordIsSet) {
                        TextField {
                            label = ReactNode("Current password")
                            required = true
                            type = InputType.password
                            margin = FormControlMargin.dense
                            variant = FormControlVariant.outlined
                            value = currentPassword
                            helperText = currentPasswordError?.let { ReactNode(it) }
                            error = currentPasswordError != null
                            onChange = { currentPassword = it.eventValue() }
                        }
                    } else {
                        Typography {
                            +"You have no password set."
                        }
                    }
                    TextField {
                        label = ReactNode("New password")
                        required = true
                        type = InputType.password
                        margin = FormControlMargin.dense
                        variant = FormControlVariant.outlined
                        value = newPassword
                        helperText = newPasswordError?.let { ReactNode(it) }
                        error = newPasswordError != null
                        onChange = { newPassword = it.eventValue() }
                    }
                    TextField {
                        label = ReactNode("Confirm new password")
                        required = true
                        type = InputType.password
                        margin = FormControlMargin.dense
                        variant = FormControlVariant.outlined
                        value = newPasswordRepeat
                        helperText = newPasswordRepeatError?.let { ReactNode(it) }
                        error = newPasswordRepeatError != null
                        onChange = { newPasswordRepeat = it.eventValue() }
                    }
                }
            }

            CardActions {
                sx {
                    padding = themed(2)
                }
                Button {
                    type = ButtonType.submit
                    variant = ButtonVariant.contained
                    disabled = props.disabled
                    if (appState.myPasswordIsSet) {
                        +"Change password"
                    } else {
                        +"Set password"
                    }
                }
                if (appState.myPasswordIsSet)
                    Button {
                        onClick = { resetPassword() }
                        variant = ButtonVariant.contained
                        color = ButtonColor.secondary
                        disabled = props.disabled
                        +"Reset password"
                    }
            }
        }
    }
}

val UserSettings = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val user = appState.session.user ?: run {
        console.error("No user")
        return@FC
    }


    var alertSeverity by useState<AlertColor>(AlertColor.success)
    var alertContent by useState<String>("")
    var alertOpen by useState<Boolean>(false)
    fun closeSnackbar() { alertOpen = false }

    AlertSnackbar {
        open = alertOpen
        autoHideDuration = 6000
        onClose = {_, reason -> if (reason != SnackbarCloseReason.clickaway) closeSnackbar()}
        severity = alertSeverity
        +alertContent
    }

    fun onSuccess(message: String) {
        alertOpen = true
        alertContent = message
        alertSeverity = AlertColor.success
    }

    fun onError(message: String) {
        showError?.invoke(message)
    }

    Container {
        maxWidth = byTheme("sm")

        Typography {
            sx {
                marginBottom = themed(4)
            }
            variant = TypographyVariant.h2
            +"User settings"
        }

        Stack {
            spacing = themed(2)

            listOf(UserSettingsName, UserSettingsEmail, UserSettingsPassword).map {
                it {
                    this.user = user
                    this.disabled = stale
                    this.onSuccess = ::onSuccess
                    this.onError = ::onError
                }
            }
        }
    }
}
