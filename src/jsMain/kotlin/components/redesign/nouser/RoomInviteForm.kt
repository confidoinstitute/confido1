package components.redesign.nouser

import components.LoginContext
import components.redesign.basic.Alert
import components.redesign.basic.MainPalette
import components.redesign.basic.Stack
import components.redesign.forms.TextInput
import components.redesign.rooms.RoomInviteCore
import components.redesign.rooms.RoomInviteFormProps
import components.showError
import csstype.*
import emotion.react.css
import io.ktor.client.call.*
import payloads.requests.AcceptInviteAndCreateUser
import react.FC
import react.Props
import react.dom.html.InputType
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.router.useNavigate
import react.useContext
import react.useState
import utils.isEmailValid
import utils.roomUrl
import utils.runCoroutine

val RoomInviteNoUser = FC<Props> {
    console.log("NO USER CREATE")
    RoomInviteCore {
        form = RoomInviteFormNoUser
    }
}

private val RoomInviteFormNoUser = FC<RoomInviteFormProps> { props ->
    val palette = MainPalette.login

    val loginState = useContext(LoginContext)
    val emailRequired = !props.allowAnonymous

    var name by useState("")
    var email by useState("")
    var emailError by useState<String?>(null)

    // TODO: Consider just redirecting to login instead to simplify this component.
    var loginRequired by useState(false)

    val navigate = useNavigate()

    fun acceptInvite() {
        val userMail = email.trim().ifEmpty { null }
        val emailValid = userMail?.let { isEmailValid(it) } ?: false
        emailError = null

        if (userMail != null && !emailValid) {
            emailError = "This email address is not valid."
        } else {
            if (emailRequired && userMail == null) {
                emailError = "An email address is required."
            } else {
                runCoroutine {
                    Client.sendData("${roomUrl(props.roomId)}/invite/accept_newuser",
                        AcceptInviteAndCreateUser(
                            props.inviteToken,
                            name.trim().ifEmpty { null },
                            userMail
                        ),
                        onError = { showError?.invoke(it) }
                    ) {
                        if (body()) {
                            // We need to log in.
                            loginRequired = true
                        } else {
                            loginState.login()
                            navigate("/room/${props.roomId}")
                        }
                    }
                }
            }
        }
    }

    Stack {
        css {
            color = palette.text.color
            textAlign = TextAlign.center
            fontSize = 14.px
            fontFamily = FontFamily.sansSerif
            gap = 12.px
        }
        div {
            +"You have been invited to room "
            b {
                +props.roomName
            }
            +". Use your email to log in so that you can come back later."
        }

        if (!loginRequired) {
            Stack {
                css {
                    gap = 1.px
                }
                TextInput {
                    className = loginInputClass
                    type = InputType.email
                    required = emailRequired
                    value = email
                    placeholder = if (emailRequired) {
                        "Email"
                    } else {
                        "Email (optional)"
                    }
                    value = email
                    onChange = {
                        email = it.target.value
                        emailError = null
                    }
                }

                TextInput {
                    className = loginInputClass
                    placeholder = "Name (optional)"
                    value = name
                    onChange = {
                        name = it.target.value
                    }
                }
            }

            components.redesign.forms.Button {
                css {
                    width = 100.pct
                    borderRadius = 10.px
                }
                this.palette = MainPalette.default
                onClick = { acceptInvite() }
                +"Start forecasting"
            }

            if (emailError != null) {
                Alert {
                    +emailError!!
                }
            }
        } else {
            LoginForm {
                prefilledEmail = email
            }
        }
    }
}
