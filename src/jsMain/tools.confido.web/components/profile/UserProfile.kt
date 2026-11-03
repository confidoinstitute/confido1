package tools.confido.web.components.profile

import tools.confido.web.ApiError
import tools.confido.web.Client
import tools.confido.api.Api
import tools.confido.web.components.*
import tools.confido.web.components.basic.PageHeader
import tools.confido.web.components.basic.Stack
import tools.confido.web.components.forms.*
import emotion.react.css
import web.cssom.*
import tools.confido.web.hooks.useCoroutineLock
import tools.confido.payloads.requests.SetNick
import tools.confido.payloads.requests.RemovePassword
import tools.confido.payloads.requests.SetPassword
import tools.confido.payloads.requests.StartEmailVerification
import react.ChildrenBuilder
import react.FC
import react.Props
import react.PropsWithChildren
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.useState
import tools.confido.users.*
import web.autofill.AutoFill
import web.html.ButtonType
import web.html.InputType
import web.html.button
import web.html.email
import web.html.password
import web.html.submit

/**
 * The profile page: nickname, e-mail and password are independent sections, each
 * saved on its own (one endpoint each), with errors and confirmations inline.
 */
val UserProfile = FC<Props> {
    PageHeader {
        navigateBack = "/"
        title = "User settings"
    }

    ThemeProvider {
        theme = { theme -> theme.copy(colors = theme.colors.copy(form = AltFormColors)) }
        NickSection {}
        EmailSection {}
        PasswordSection {}
    }
}

/** Runs an API call, routing its [ApiError] into [onError] (inline) instead of the snackbar. */
private suspend fun inlineErrors(onError: (String?) -> Unit, block: suspend () -> Unit) {
    onError(null)
    try {
        block()
    } catch (e: ApiError) {
        onError(e.message)
    }
}

private external interface SaveButtonProps : PropsWithChildren {
    var disabled: Boolean
}

private val SaveButton = FC<SaveButtonProps> { props ->
    Stack {
        css { flexGrow = number(1.0) }
        Button {
            type = ButtonType.submit
            css {
                display = Display.block
                margin = Margin(0.px, 0.px, 0.px, Auto.auto)
                fontWeight = integer(500)
            }
            disabled = useDisconnected() || props.disabled
            +props.children
        }
    }
}

private external interface SectionFeedbackProps : Props {
    var error: String?
    var info: String?
}

/**
 * A section's outcome, right above the button that caused it: a tinted box that stays
 * until the next edit (a field comment or a 5-second snackbar is too easy to miss).
 */
private val SectionFeedback = FC<SectionFeedbackProps> { props ->
    val text = props.error ?: props.info ?: return@FC
    val isError = props.error != null
    div {
        role = if (isError) AriaRole.alert else AriaRole.status
        css {
            display = Display.flex
            alignItems = AlignItems.center
            gap = 10.px
            padding = Padding(10.px, 12.px)
            borderRadius = 5.px
            fontSize = 14.px
            lineHeight = 18.px
            if (isError) {
                backgroundColor = Color("#FDECEC")
                color = Color("#B3261E")
            } else {
                backgroundColor = Color("#E6F4EA")
                color = Color("#1E6B34")
            }
        }
        if (isError) b { +"!" } else CheckmarkIcon {}
        div { +text }
    }
}

/** Field content stacked above its inline actions (FormField lays children out in a row). */
private val FieldColumn = FC<PropsWithChildren> { props ->
    Stack {
        css {
            width = 100.pct
            gap = 7.px
            alignItems = AlignItems.flexStart
        }
        +props.children
    }
}

private fun ChildrenBuilder.inlineAction(text: String, disabled: Boolean, onClick: () -> Unit) =
    TextButton {
        type = ButtonType.button
        css {
            margin = 0.px
            alignSelf = AlignSelf.flexStart
        }
        this.disabled = disabled
        this.onClick = { onClick() }
        +text
    }

private val NickSection = FC<Props> {
    val user = useMyUser()
    var nick by useState(user?.nick ?: "")
    var error by useState<String?>(null)
    var info by useState<String?>(null)
    val save = useCoroutineLock()
    val unchanged = sanitizeNick(nick).ifEmpty { null } == user?.nick

    Form {
        onSubmit = {
            save {
                inlineErrors({ error = it }) {
                    Client.call(Api.profileNick(), SetNick(nick))
                    nick = sanitizeNick(nick)
                    info = "Nickname saved."
                }
            }
        }
        FormSection {
            title = "Nickname"
            FormField {
                title = "Display nickname"
                TextInput {
                    name = "nick"
                    value = nick
                    onChange = { nick = it.target.value; error = null; info = null }
                }
                comment = "Your nickname may appear whenever you comment or answer questions. Leave it empty to appear as an anonymous user."
            }
            SectionFeedback {
                this.error = error
                this.info = info
            }
            SaveButton {
                disabled = unchanged || save.running
                +"Save nickname"
            }
        }
    }
}

private val EmailSection = FC<Props> {
    val user = useMyUser()
    val pending = useMyPendingEmail()
    var newEmail by useState("")
    var error by useState<String?>(null)
    var info by useState<String?>(null)
    val action = useCoroutineLock()

    fun startVerification(address: String) = action {
        inlineErrors({ error = it }) {
            Client.call(Api.profileEmailStartVerification(), StartEmailVerification(address.trim()))
            info = "We sent a verification link to ${address.trim()}. Open it to confirm the address."
            newEmail = ""
        }
    }

    Form {
        onSubmit = {
            if (!isEmailValid(newEmail.trim())) { info = null; error = "This is not a valid e-mail address." }
            else startVerification(newEmail)
        }
        FormSection {
            title = "E-mail"
            FormField {
                title = "Current e-mail address"
                FieldColumn {
                    div {
                        if (user?.email != null) b { +user.email } else +"None"
                    }
                    if (user?.email != null && !user.emailVerified && pending == null)
                        inlineAction("Send verification e-mail", action.running) { startVerification(user.email) }
                }
                comment = when {
                    user?.email == null ->
                        "Without an e-mail address you cannot log in again after your session ends."
                    user.emailVerified ->
                        "Verified. Used for logging in; may be shown to other members of the organization."
                    else -> "Not verified yet."
                }
            }
            if (pending != null)
                FormField {
                    title = "Waiting for verification"
                    comment = "The new address replaces the current one once you open the link we sent to it."
                    FieldColumn {
                        div { b { +pending } }
                        Stack {
                            direction = FlexDirection.row
                            css { gap = 15.px }
                            inlineAction("Resend link", action.running) { startVerification(pending) }
                            inlineAction("Cancel change", action.running) {
                                action {
                                    inlineErrors({ error = it }) {
                                        Client.call(Api.profileEmailCancel())
                                        info = "The e-mail change was cancelled."
                                    }
                                }
                            }
                        }
                    }
                }
            FormField {
                title = if (user?.email == null) "Add e-mail address" else "Change e-mail address"
                TextInput {
                    name = "email"
                    type = InputType.email
                    placeholder = "New e-mail address"
                    value = newEmail
                    onChange = { newEmail = it.target.value; error = null; info = null }
                }
                comment = if (user?.email == null) "We will send a verification link to the new address."
                    else "We will send a verification link to the new address, and a notice to the current one once it changes."
            }
            SectionFeedback {
                this.error = error
                this.info = info
            }
            SaveButton {
                disabled = newEmail.isBlank() || action.running
                +"Send verification link"
            }
        }
    }
}

private val PasswordSection = FC<Props> {
    val user = useMyUser()
    // Read during render: context accessors throw when called from event handlers.
    val passwordIsSet = useMyPasswordIsSet()
    var currentPassword by useState("")
    var newPassword by useState("")
    var confirmPassword by useState("")
    var error by useState<String?>(null)
    var info by useState<String?>(null)
    val action = useCoroutineLock()

    fun save() = action {
        if (newPassword != confirmPassword) {
            error = "The passwords do not match."
            return@action
        }
        when (checkPassword(newPassword)) {
            PasswordCheckResult.OK -> {}
            PasswordCheckResult.TOO_SHORT -> {
                error = "Please choose a password at least $MIN_PASSWORD_LENGTH characters long."
                return@action
            }
            PasswordCheckResult.TOO_LONG -> {
                error = "Please choose a password at most $MAX_PASSWORD_LENGTH characters long."
                return@action
            }
        }
        inlineErrors({ error = it }) {
            Client.call(Api.profilePassword(), SetPassword(currentPassword.takeIf { passwordIsSet }, newPassword))
            info = "Password saved. Your other sessions were logged out."
            currentPassword = ""
            newPassword = ""
            confirmPassword = ""
        }
    }

    fun removePassword() = action {
        if (currentPassword.isEmpty()) {
            error = "Enter your current password to remove it."
            return@action
        }
        inlineErrors({ error = it }) {
            Client.call(Api.profilePasswordRemove(), RemovePassword(currentPassword))
            info = "Password removed: you log in with e-mail links now. Your other sessions were logged out."
            currentPassword = ""
            newPassword = ""
            confirmPassword = ""
        }
    }

    fun sendResetLink() = action {
        inlineErrors({ error = it }) {
            Client.call(Api.profilePasswordReset())
            info = "We sent a link to ${user?.email}. It lets you choose a new password without the current one."
        }
    }

    Form {
        onSubmit = { save() }
        FormSection {
            title = "Password"
            if (user?.email == null) {
                FormField {
                    title = "Password"
                    comment = "Add an e-mail address first: you log in with it and the password."
                }
                return@FormSection
            }
            FormField {
                title = if (passwordIsSet) "Change password" else "Set a password"
                Stack {
                    css {
                        width = 100.pct
                        gap = 7.px
                    }
                    if (passwordIsSet)
                        TextInput {
                            name = "current_password"
                            type = InputType.password
                            autoComplete = "current-password".unsafeCast<AutoFill>()
                            placeholder = "Current password"
                            value = currentPassword
                            onChange = { currentPassword = it.target.value; error = null; info = null }
                        }
                    TextInput {
                        name = "new_password"
                        type = InputType.password
                        autoComplete = "new-password".unsafeCast<AutoFill>()
                        placeholder = "New password"
                        value = newPassword
                        onChange = { newPassword = it.target.value; error = null; info = null }
                    }
                    TextInput {
                        name = "confirm_password"
                        type = InputType.password
                        autoComplete = "new-password".unsafeCast<AutoFill>()
                        placeholder = "Confirm new password"
                        value = confirmPassword
                        onChange = { confirmPassword = it.target.value; error = null; info = null }
                    }
                    if (passwordIsSet)
                        inlineAction("Forgot your current password? Reset it by e-mail", action.running) { sendResetLink() }
                }
                comment = if (passwordIsSet) null
                    else "You have no password yet and log in with e-mail links. A password lets you log in directly."
            }
            SectionFeedback {
                this.error = error
                this.info = info
            }
            Stack {
                direction = FlexDirection.row
                css { alignItems = AlignItems.center }
                if (passwordIsSet)
                    inlineAction("Remove password", action.running) { removePassword() }
                SaveButton {
                    disabled = newPassword.isEmpty() || action.running
                    +(if (passwordIsSet) "Change password" else "Set password")
                }
            }
        }
    }
}
