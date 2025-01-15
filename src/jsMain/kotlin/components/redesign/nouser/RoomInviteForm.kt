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
import users.UserType
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
    var nameError by useState<String?>(null)

    // TODO: Consider just redirecting to login instead to simplify this component.
    var loginRequired by useState(false)

    val nameRequired = props.requireNickname
    val nameUnique = props.preventDuplicateNicknames
    val nameScope = if (props.targetUserType == UserType.MEMBER) "workspace" else "room"

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
                        onError = { error ->
                            when {
                                // XXX make this more structured
                                error.contains("nickname is already taken", ignoreCase = true) == true -> {
                                    nameError = if (props.targetUserType == UserType.MEMBER)
                                        "This name is already taken in the workspace"
                                    else
                                        "This name is already taken in this room"
                                }
                                else -> showError(error)
                            }
                        }
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
                +". "
                if (props.targetUserType == UserType.MEMBER) {
                    +"You will be able to access all workspace features. "
                } else {
                    +"You will have access to this room only. "
                }
                +"Use your email to log in so that you can come back later."
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
                    css {
                        if (nameError != null) {
                            border = Border(1.px, LineStyle.solid, Color("#F35454"))
                        }
                    }
                    placeholder = if (nameRequired) "Name (required)" else "Name (optional)"
                    value = name
                    onChange = {
                        name = it.target.value
                        nameError = null
                    }
                }

                if (nameError != null) {
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
                        +nameError!!
                    }
                }

                if (nameUnique) {
                    p {
                        css {
                            marginTop = 6.px
                            color = Color("#666666")
                            width = 100.pct
                            fontSize = 12.px
                            lineHeight = 15.px
                            fontWeight = integer(400)
                            fontFamily = sansSerif
                        }
                        +"Name must be unique in this $nameScope"
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
                onClick = onClick@{
                    // Validate name if required
                    if (nameRequired && name.trim().isEmpty()) {
                        nameError = "A name is required."
                        return@onClick
                    }
                    acceptInvite()
                }
                +"Start forecasting"
            }
        } else {
            LoginForm {
                prefilledEmail = email
            }
        }
    }
}
