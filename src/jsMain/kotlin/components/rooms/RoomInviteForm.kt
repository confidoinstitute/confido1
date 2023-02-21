package components.rooms

import components.AppStateContext
import components.LoginContext
import components.RouterLink
import components.nouser.LoginForm
import components.showError
import csstype.*
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.js.get
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import payloads.requests.*
import react.*
import tools.confido.refs.Ref
import react.dom.onChange
import react.router.useParams
import payloads.responses.InviteStatus
import react.dom.html.InputType
import react.dom.html.ReactHTML.code
import react.dom.html.ReactHTML.em
import react.router.useNavigate
import rooms.Room
import tools.confido.refs.eqid
import tools.confido.utils.TOKEN_LEN
import utils.*
import web.location.location

val RoomInviteNoUser = FC<Props> {
    console.log("Room Invite no user")
    RoomInviteCore {
        form = RoomInviteFormNoUser
    }
}

val RoomInviteLoggedIn = FC<Props> {
    RoomInviteCore {
        topAlert = MissingEmailConditionalAlert
        form = RoomInviteFormLoggedIn
    }
}
external interface InvalidInviteAlertProps: Props {
    var tooShort: Boolean
}
private val InvalidInviteAlert = FC<InvalidInviteAlertProps> { props->
    Alert {
        severity = AlertColor.error
        AlertTitle {
            +"Invalid invite link"
        }
        if(props.tooShort) {
            +"The invite link "
            code{+location.href}
            + " appears too short. Please ensure you have pasted it whole."
        } else {
            +"The invite link "
            code{+location.href}
            +" is not valid. Please ensure you have pasted it correctly. It may also have been disabled or have expired."
        }
    }
}

external interface RoomInviteFormProps : Props {
    var roomId: String
    var inviteToken: String
    var allowAnonymous: Boolean
}

private val RoomInviteFormNoUser = FC<RoomInviteFormProps> { props ->
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


    if (!loginRequired) {
        TextField {
            sx {
                marginTop = themed(2)
            }
            margin = FormControlMargin.normal
            variant = FormControlVariant.outlined
            fullWidth = true
            type = InputType.email
            id = "email-field"
            required = emailRequired
            label = if (emailRequired) {
                ReactNode("Email")
            } else {
                ReactNode("Email (optional)")
            }
            error = emailError != null
            helperText = if (emailError != null) {
                ReactNode(emailError!!)
            } else {
                ReactNode("You can log in with this email to return to your forecasts later.")
            }
            value = email
            onChange = {
                email = it.eventValue()
                emailError = null
            }
        }

        TextField {
            margin = FormControlMargin.normal
            variant = FormControlVariant.outlined
            fullWidth = true
            id = "name-field"
            label = ReactNode("Name (optional)")
            value = name
            onChange = {
                name = it.eventValue()
            }
        }

        Button {
            sx {
                marginTop = themed(1)
            }
            variant = ButtonVariant.contained
            fullWidth = true
            onClick = { acceptInvite() }
            +"Start forecasting"
        }
    } else {
        LoginForm {
            prefilledEmail = email
        }
    }
}

private val RoomInviteFormLoggedIn = FC<RoomInviteFormProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val navigate = useNavigate()

    val user = appState.session.user
    val missingRequiredEmail = user != null && user.email == null && !props.allowAnonymous
    val alreadyAccepted = appState.rooms[props.roomId]?.let { room ->
        room.members.any { membership -> membership.user eqid appState.session.user }
    } ?: false

    if (alreadyAccepted) {
        navigate(roomUrl(props.roomId))
    }

    Button {
        sx {
            marginTop = themed(2)
        }
        variant = ButtonVariant.contained
        onClick = {
            val accept = AcceptInvite(props.inviteToken)
            runCoroutine {
                Client.sendData("${roomUrl(props.roomId)}/invite/accept", accept, onError = { showError?.invoke(it) }) {
                    navigate(roomUrl(props.roomId))
                }
            }
        }
        disabled = stale || missingRequiredEmail
        +"Start forecasting"
    }
}

external interface RoomInviteAlertProps : Props {
    var allowAnonymous: Boolean
}

val POTENTIAL_SHORTLINK_RE = Regex("^[0-9]+$")

private val MissingEmailConditionalAlert = FC<RoomInviteAlertProps> { props ->
    val (appState, _) = useContext(AppStateContext)

    val user = appState.session.user
    val missingRequiredEmail = user != null && user.email == null && !props.allowAnonymous
    if (missingRequiredEmail) {
        Alert {
            sx {
                marginTop = themed(2)
            }
            severity = AlertColor.error

            AlertTitle {
                +"No email is set!"
            }
            +"This invite requires you to set an email before accepting. You can do so in "
            RouterLink {
                to = "/profile"
                +"user settings"
            }
            +"."
        }
    }
}

external interface RoomInviteCoreProps : Props {
    var topAlert: ComponentType<RoomInviteAlertProps>?
    var form: ComponentType<RoomInviteFormProps>
}

private val RoomInviteCore = FC<RoomInviteCoreProps> { props ->
    val inviteToken = useParams()["inviteToken"] ?: run {
        console.error("Missing invite token")
        return@FC
    }

    var inviteStatus by useState<InviteStatus?>(null)

    useEffectOnce {
        runCoroutine {
            Client.send("/join/$inviteToken/check", method= HttpMethod.Get, onError = {showError?.invoke(it)}) {
                inviteStatus = body()
            }
        }
    }

    Backdrop {
        this.open = inviteStatus == null
        this.sx { this.zIndex = integer(42) }
        CircularProgress {}
    }

    if (inviteStatus != null) {
        Container {
            maxWidth = byTheme("md")
            +props.topAlert?.create {
                this.allowAnonymous = inviteStatus!!.allowAnonymous
            }
        }

        Container {
            maxWidth = byTheme("xs")
            sx {
                marginTop = themed(4)
                padding = themed(2)
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = AlignItems.center
            }
            if (inviteStatus?.valid == true) {
                Typography {
                    variant = TypographyVariant.body1
                    +"You have been invited to room "
                    em {
                        +"${inviteStatus?.roomName}"
                    }
                }

                +props.form.create {
                    this.roomId = inviteStatus!!.roomRef!!.id
                    this.inviteToken = inviteToken
                    this.allowAnonymous = inviteStatus!!.allowAnonymous
                }
            } else {
                InvalidInviteAlert { tooShort = inviteToken.length < TOKEN_LEN && !POTENTIAL_SHORTLINK_RE.matches(inviteToken) }
            }
        }
    }
}
