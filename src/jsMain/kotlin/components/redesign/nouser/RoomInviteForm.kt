package components.redesign.nouser

import Client
import components.*
import components.redesign.basic.*
import components.redesign.rooms.*
import csstype.*
import emotion.react.*
import io.ktor.client.call.*
import payloads.requests.*
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.p
import react.router.*
import tools.confido.state.UserSessionValidity
import utils.*

val RoomInviteNoUser = FC<Props> {
    Stack {
        css {
            alignItems = AlignItems.center
        }
        Stack {
            css {
                maxWidth = 400.px
                alignItems = AlignItems.center
            }
            RoomInviteCore {
                form = RoomInviteFormNoUser
            }
        }
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
                            userMail,
                            UserSessionValidity.PERMANENT,
                        ),
                        onError = { showError(it) }
                    ) {
                        if (body()) {
                            // We need to log in.
                            loginRequired = true
                        } else {
                            loginState.login()
                            navigate(roomUrl(props.roomId))
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
            fontFamily = sansSerif
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
                LoginInput {
                    css {
                        if (emailError != null) {
                            border = Border(1.px, LineStyle.solid, Color("#F35454"))
                        }
                    }
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

                LoginInput {
                    placeholder = "Name (optional)"
                    value = name
                    onChange = {
                        name = it.target.value
                    }
                }

                if (emailError != null) {
                    p {
                        css {
                            marginTop = 6.px
                            color = Color("#F35454")
                            width = 100.pct

                            fontSize = 12.px
                            lineHeight = 15.px
                            fontWeight = integer(400)
                            fontFamily = sansSerif
                        }
                        +(emailError ?: "")
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
        } else {
            LoginForm {
                prefilledEmail = email
            }
        }
    }
}
