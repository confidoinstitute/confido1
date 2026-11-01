package tools.confido.web.components.profile

import tools.confido.web.ApiError
import tools.confido.web.Client
import tools.confido.api.Api
import tools.confido.web.components.basic.Stack
import tools.confido.web.components.basic.sansSerif
import tools.confido.web.components.forms.*
import tools.confido.web.hooks.useCoroutineLock
import emotion.react.css
import js.array.component1
import js.array.component2
import web.cssom.*
import tools.confido.payloads.requests.PasswordResetFinish
import tools.confido.users.*
import react.FC
import react.Props
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.span
import react.router.dom.Link
import react.router.dom.useSearchParams
import react.useState
import web.autofill.AutoFill
import web.html.ButtonType
import web.html.InputType
import web.html.password
import web.html.submit

/**
 * Target of mailed password-reset links (`/password_reset?t=…`), both the requested
 * reset and the "password changed — not you?" notice: choose a new password. Works
 * logged in or out; the link's token is the credential.
 */
val PasswordResetPage = FC<Props> {
    val (searchParams, _) = useSearchParams()
    val token = searchParams.get("t") ?: ""

    var newPassword by useState("")
    var confirmPassword by useState("")
    var error by useState<String?>(null)
    var done by useState(false)
    val submit = useCoroutineLock()

    fun save() = submit {
        error = when {
            newPassword != confirmPassword -> "The passwords do not match."
            checkPassword(newPassword) == PasswordCheckResult.TOO_SHORT ->
                "Please choose a password at least $MIN_PASSWORD_LENGTH characters long."
            checkPassword(newPassword) == PasswordCheckResult.TOO_LONG ->
                "Please choose a password at most $MAX_PASSWORD_LENGTH characters long."
            else -> null
        }
        if (error != null) return@submit
        try {
            Client.call(Api.profilePasswordResetFinish(), PasswordResetFinish(token, newPassword))
            done = true
        } catch (e: ApiError) {
            error = e.message
        }
    }

    Stack {
        css {
            textAlign = TextAlign.center
            fontSize = 16.px
            fontFamily = sansSerif
            gap = 12.px
            width = 100.pct
            maxWidth = 400.px
            margin = Margin(20.px, Auto.auto)
            padding = Padding(0.px, 15.px)
        }
        if (done) {
            b { +"Your password was changed" }
            span { +"Everyone else logged into your account was logged out. You can log in with the new password." }
        } else {
            b { +"Choose a new password" }
            Form {
                onSubmit = { save() }
                Stack {
                    css { gap = 7.px }
                    TextInput {
                        name = "new_password"
                        type = InputType.password
                        autoComplete = "new-password".unsafeCast<AutoFill>()
                        placeholder = "New password"
                        value = newPassword
                        onChange = { newPassword = it.target.value; error = null }
                    }
                    TextInput {
                        name = "confirm_password"
                        type = InputType.password
                        autoComplete = "new-password".unsafeCast<AutoFill>()
                        placeholder = "Confirm new password"
                        value = confirmPassword
                        onChange = { confirmPassword = it.target.value; error = null }
                    }
                    error?.let {
                        span {
                            css { color = Color("#F35454"); fontSize = 13.px }
                            +it
                        }
                    }
                    Button {
                        type = ButtonType.submit
                        disabled = newPassword.isEmpty() || submit.running
                        +"Set new password"
                    }
                }
            }
        }
        Link {
            to = "/"
            +"Go back"
        }
    }
}
