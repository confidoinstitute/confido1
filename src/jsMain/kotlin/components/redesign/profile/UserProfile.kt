package components.redesign.profile

import components.AppStateContext
import components.redesign.*
import components.redesign.basic.Alert
import components.redesign.basic.PageHeader
import components.redesign.basic.Stack
import components.redesign.forms.*
import components.showError
import emotion.react.css
import csstype.*
import hooks.useCoroutineLock
import io.ktor.client.call.*
import io.ktor.http.*
import payloads.requests.EditProfile
import payloads.responses.EditProfileResult
import react.FC
import react.Props
import react.dom.html.ButtonType
import react.dom.html.InputType
import react.useContext
import react.useState
import users.*
import utils.isEmailValid
import utils.runCoroutine

val UserProfile = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val user = appState.session.user

    var nick by useState(user?.nick ?: "")

    var email by useState(user?.email ?: "")
    var emailPending by useState(false)

    var oldPassword by useState("")
    var newPassword by useState("")
    var confirmPassword by useState("")
    var passwordReset by useState(false)

    var editResult by useState<EditProfileResult?>(null)

    val edit = useCoroutineLock()

    fun resetPassword() = runCoroutine {
        Client.send("/profile/password/reset", onError = { showError(it) }) { passwordReset = true }
        oldPassword = ""
        newPassword = ""
        confirmPassword = ""
    }

    fun editProfile() = edit {
        if (!isEmailValid(email)) {
            editResult = EditProfileResult(emailError = "This is not a valid e-mail address.")
            return@edit
        }
        if (newPassword != confirmPassword) {
            editResult = EditProfileResult(passwordError = "The passwords do not match.")
            return@edit
        }

        val changingPassword = oldPassword.isNotEmpty() || newPassword.isNotEmpty() || confirmPassword.isNotEmpty()
        if (changingPassword)
            when (checkPassword(newPassword)) {
                PasswordCheckResult.OK -> {}
                PasswordCheckResult.TOO_SHORT -> {
                    editResult = EditProfileResult(passwordError = "Please choose a password at least $MIN_PASSWORD_LENGTH characters long.")
                    return@edit
                }
                PasswordCheckResult.TOO_LONG -> {
                    editResult = EditProfileResult(passwordError = "Please choose a password at most $MAX_PASSWORD_LENGTH characters long.")
                    return@edit
                }
            }

        Client.sendData("/profile/edit", EditProfile(
                nick, email,
                if (appState.myPasswordIsSet) oldPassword else null,
                newPassword.ifEmpty { null }),
            onError = {showError(it)}) {
            editResult = body()
        }
    }

    PageHeader {
        navigateBack = "/"
        title = "Edit profile"
        action = "Save"
        disabledAction = (stale || edit.running)
        onAction = { editProfile() }
    }

    ThemeProvider {
        theme = { theme -> theme.copy(colors = theme.colors.copy(form = AltFormColors)) }
        Form {
            onSubmit = {
                editProfile()
            }
            FormSection {
                FormField {
                    title = "Display nickname"
                    required = true
                    TextInput {
                        name = "nick"
                        value = nick
                        onChange = { nick = it.target.value }
                    }
                    comment = if (editResult?.nickChanged == true)
                        "Nickname was set."
                    else
                        "Your nickname may appear whenever you comment or answer questions. You can change it at any time."
                }

                if (user?.emailVerified == false)
                    Alert {
                        +"Your email is not verified!"
                    }

                FormField {
                    title = "E-mail address"
                    required = true
                    TextInput {
                        name = "email"
                        value = email
                        type = InputType.email
                        onChange = { email = it.target.value }
                    }
                    error = editResult?.emailError
                    comment = if (editResult?.emailChanged == true)
                        "We sent you a verification e-mail, please check your inbox and verify your e-mail."
                    else
                        "This email address is used for logging into your account and may be shown to other members of the organization."
                }

                FormField {
                    title = "Password"
                    Stack {
                        css {
                            width = 100.pct
                            gap = 7.px
                        }

                        if (appState.myPasswordIsSet)
                            TextInput {
                                name = "old_password"
                                type = InputType.password
                                placeholder = "Old password"
                                value = oldPassword
                                onChange = { oldPassword = it.target.value }
                            }
                        TextInput {
                            name = "new_password"
                            type = InputType.password
                            placeholder = "New password"
                            value = newPassword
                            onChange = { newPassword = it.target.value }
                        }
                        TextInput {
                            name = "confirm_password"
                            type = InputType.password
                            placeholder = "Confirm new password"
                            value = confirmPassword
                            onChange = { confirmPassword = it.target.value }
                        }
                        if (appState.myPasswordIsSet && !passwordReset)
                            TextButton {
                                type = ButtonType.button
                                css {
                                    margin = 0.px
                                }
                                onClick = { resetPassword() }
                                +"Reset password"
                            }
                    }
                    error = editResult?.passwordError
                    comment = if (!appState.myPasswordIsSet)
                        "Your password is not set."
                    else if (passwordReset)
                        "Password reset request was sent."
                    else if (editResult?.passwordChanged == true)
                        "Password was set."
                    else ""
                }

                Stack {
                    Button {
                        type = ButtonType.submit
                        css {
                            display = Display.block
                            margin = Margin(20.px, 20.px, 10.px)
                            fontWeight = integer(500)
                        }
                        +"Edit profile"
                        disabled = (stale || edit.running)
                    }
                }
            }
        }
    }
}