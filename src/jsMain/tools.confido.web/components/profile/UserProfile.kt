package tools.confido.web.components.profile

import tools.confido.web.Client
import tools.confido.api.Api
import tools.confido.web.components.useMyPasswordIsSet
import tools.confido.web.components.useMyUser
import tools.confido.web.components.useDisconnected
import tools.confido.web.components.*
import tools.confido.web.components.basic.Alert
import tools.confido.web.components.basic.PageHeader
import tools.confido.web.components.basic.Stack
import tools.confido.web.components.basic.showError
import tools.confido.web.components.forms.*
import emotion.react.css
import web.cssom.*
import tools.confido.web.hooks.useCoroutineLock
import io.ktor.client.call.*
import io.ktor.http.*
import tools.confido.payloads.requests.EditProfile
import tools.confido.payloads.requests.StartEmailVerification
import tools.confido.payloads.responses.EditProfileResult
import react.FC
import react.Props
import react.use
import react.useState
import tools.confido.users.*
import tools.confido.web.utils.isEmailValid
import web.html.ButtonType
import web.html.InputType
import web.html.button
import web.html.email
import web.html.password
import web.html.submit

val UserProfile = FC<Props> {
    val user = useMyUser()
    // Read during render: context accessors throw when called from event handlers.
    val passwordIsSet = useMyPasswordIsSet()
    // Users without an e-mail (e.g. guests invited without one) may leave it empty.
    val emailRequired = user?.email != null

    var nick by useState(user?.nick ?: "")

    var email by useState(user?.email ?: "")
    var verificationResent by useState(false)

    var oldPassword by useState("")
    var newPassword by useState("")
    var confirmPassword by useState("")
    var passwordReset by useState(false)

    var editResult by useState<EditProfileResult?>(null)

    val edit = useCoroutineLock()
    val sideAction = useCoroutineLock()

    fun resendVerification() = sideAction {
        val address = user?.email ?: return@sideAction
        Client.call(Api.profileEmailStartVerification(), StartEmailVerification(address))
        verificationResent = true
    }

    fun resetPassword() = sideAction {
        Client.call(Api.profilePasswordReset())
        passwordReset = true
        oldPassword = ""
        newPassword = ""
        confirmPassword = ""
    }

    fun editProfile() = edit {
        if ((emailRequired || email.isNotBlank()) && !isEmailValid(email.trim())) {
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

        editResult = Client.call(Api.profileEdit(), EditProfile(
                nick, email,
                if (passwordIsSet) oldPassword else null,
                newPassword.ifEmpty { null }))
    }

    PageHeader {
        navigateBack = "/"
        title = "Edit profile"
        action = "Save"
        disabledAction = edit.running
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

                if (user?.email != null && !user.emailVerified)
                    Alert {
                        +"Your email is not verified! "
                        if (verificationResent)
                            +"We sent you a new verification e-mail."
                        else
                            TextButton {
                                type = ButtonType.button
                                css { margin = 0.px }
                                disabled = sideAction.running
                                onClick = { resendVerification() }
                                +"Resend verification e-mail"
                            }
                    }

                FormField {
                    title = "E-mail address"
                    required = emailRequired
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

                        if (passwordIsSet)
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
                        if (passwordIsSet && !passwordReset)
                            TextButton {
                                type = ButtonType.button
                                css {
                                    margin = 0.px
                                }
                                disabled = sideAction.running
                                onClick = { resetPassword() }
                                +"Reset password"
                            }
                    }
                    error = editResult?.passwordError
                    comment = if (!passwordIsSet)
                        "Your password is not set."
                    else if (passwordReset)
                        "We sent you an e-mail. Its link removes your current password; then you can set a new one here without the old one."
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
                        disabled = useDisconnected() || edit.running
                    }
                }
            }
        }
    }
}