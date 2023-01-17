package components.profile

import components.AppStateContext
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
import react.dom.html.InputType
import react.dom.html.ReactHTML.br
import users.*
import utils.*
import kotlin.coroutines.EmptyCoroutineContext

val UserSettings = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val user = appState.session.user ?: run {
        console.error("No user")
        return@FC
    }

    var name by useState(user.nick ?: "")
    var email by useState(user.email ?: "")

    var emailError by useState<String?>(null)
    var currentPasswordError by useState<String?>(null)
    var newPasswordError by useState<String?>(null)
    var newPasswordRepeatError by useState<String?>(null)

    var currentPassword by useState("")
    var newPassword by useState("")
    var newPasswordRepeat by useState("")

    var nameUpdated by useState(false)
    var passwordUpdated by useState(false)

    // Note that this is currently only stored on the frontend.
    // Navigating away from user settings and coming back will show the user the old email
    // without any verification prompt.
    var pendingEmailChange by useState<String?>(null)

    fun changeEmail() {
        val changed = (user.email ?: "") != email
        if (!changed) {
            emailError = "This is already your current email address.";
            return
        }
        if (!isEmailValid(email)) {
            emailError = "This email address is not valid."
            return
        }

        Client.postData("/profile/email/start_verification", StartEmailVerification(email))
        emailError = null
        pendingEmailChange = email
    }

    fun changeName() {
        Client.postData("/profile/nick", SetNick(name))
        nameUpdated = true
    }

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

        CoroutineScope(EmptyCoroutineContext).launch {
            val response = Client.httpClient.postJson("/profile/password", setPassword) {}
            if (response.status == HttpStatusCode.OK) {
                passwordUpdated = true
                currentPassword = ""
                newPassword = ""
                newPasswordRepeat = ""
            } else if (response.status == HttpStatusCode.Unauthorized) {
                currentPasswordError = "The current password is incorrect."
            }
        }
    }


    fun removePassword() {
        CoroutineScope(EmptyCoroutineContext).launch {
            val response = Client.httpClient.delete("/profile/password") {}
            if (response.status == HttpStatusCode.OK) {
                passwordUpdated = true
            }
            currentPassword = ""
            newPassword = ""
            newPasswordRepeat = ""
        }
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
            spacing = themed(2);

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
                                onClick = {
                                    Client.postData("/profile/email/start_verification", StartEmailVerification(email))
                                }
                                disabled = stale

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

            if (nameUpdated) {
                Alert {
                    severity = AlertColor.success
                    +"Your name has been updated."
                }
            }

            if (passwordUpdated) {
                Alert {
                    severity = AlertColor.success
                    if (appState.myPasswordIsSet) {
                        +"Your password has been updated."
                    } else {
                        +"Your password has been removed."
                    }
                }
            }

            val textFieldVariant = FormControlVariant.outlined

            Card {
                CardHeader {
                    title = ReactNode("Profile")
                }
                CardContent {
                    Stack {
                        TextField {
                            variant = textFieldVariant
                            id = "name-field"
                            label = ReactNode("Name")
                            helperText =
                                ReactNode("Your name may appear whenever you comment or make predictions. You can change it at any time.")
                            value = name
                            disabled = stale
                            onChange = {
                                name = it.eventValue()
                            }
                            onKeyUp = {
                                if (it.key == "Enter") {
                                    changeName()
                                }
                            }
                        }
                    }
                }
                CardActions {
                    sx {
                        padding = themed(2)
                    }
                    Button {
                        onClick = { changeName() }
                        variant = ButtonVariant.contained
                        disabled = stale

                        if (user.nick == null) {
                            +"Set name"
                        } else {
                            +"Update name"
                        }
                    }
                }
            }

            Card {
                CardHeader {
                    title = ReactNode("Email address")
                }

                CardContent {
                    // TODO: Better explanation of email visibility
                    Stack {
                        TextField {
                            variant = textFieldVariant
                            id = "email-field"
                            label = ReactNode("Email address")
                            helperText = emailError?.let { ReactNode(it) }
                                ?: ReactNode("This email address is used for logging into your account and may be shown to other members of the organization.")
                            error = emailError != null
                            required = true
                            value = email
                            disabled = stale
                            onChange = {
                                email = it.eventValue()
                            }
                            onKeyUp = {
                                if (it.key == "Enter") {
                                    changeEmail()
                                }
                            }
                        }
                    }
                }

                CardActions {
                    sx {
                        padding = themed(2)
                    }
                    Button {
                        onClick = { changeEmail() }
                        variant = ButtonVariant.contained
                        disabled = stale
                        +"Update email"
                    }
                }
            }

            Card {
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
                                variant = textFieldVariant
                                value = currentPassword
                                helperText = currentPasswordError?.let { ReactNode(it) }
                                error = currentPasswordError != null
                                onChange = { currentPassword = it.eventValue() }
                                onKeyUp = {
                                    if (it.key == "Enter") {
                                        changePassword()
                                    }
                                }
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
                            variant = textFieldVariant
                            value = newPassword
                            helperText = newPasswordError?.let { ReactNode(it) }
                            error = newPasswordError != null
                            onChange = { newPassword = it.eventValue() }
                            onKeyUp = {
                                if (it.key == "Enter") {
                                    changePassword()
                                }
                            }
                        }
                        TextField {
                            label = ReactNode("Confirm new password")
                            required = true
                            type = InputType.password
                            margin = FormControlMargin.dense
                            variant = textFieldVariant
                            value = newPasswordRepeat
                            helperText = newPasswordRepeatError?.let { ReactNode(it) }
                            error = newPasswordRepeatError != null
                            onChange = { newPasswordRepeat = it.eventValue() }
                            onKeyUp = {
                                if (it.key == "Enter") {
                                    changePassword()
                                }
                            }
                        }
                    }
                }

                CardActions {
                    sx {
                        padding = themed(2)
                    }
                    Button {
                        onClick = { changePassword() }
                        variant = ButtonVariant.contained
                        disabled = stale
                        if (appState.myPasswordIsSet) {
                            +"Change password"
                        } else {
                            +"Set password"
                        }
                    }
                    if (appState.myPasswordIsSet)
                    Button {
                        onClick = { removePassword() }
                        variant = ButtonVariant.contained
                        color = ButtonColor.secondary
                        disabled = stale
                        +"Remove password"
                    }
                }
            }
        }
    }
}
