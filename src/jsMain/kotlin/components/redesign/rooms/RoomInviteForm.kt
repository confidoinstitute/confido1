package components.redesign.rooms

import components.AppStateContext
import components.LoginContext
import components.RouterLink
import components.nouser.LoginForm
import components.redesign.basic.MainPalette
import components.redesign.basic.TextPalette
import components.redesign.forms.LoginTextInput
import components.redesign.nouser.LoginFormRedesigned
import components.redesign.nouser.LogoWithText
import components.showError
import csstype.*
import emotion.react.css
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
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.code
import react.dom.html.ReactHTML.em
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.router.useNavigate
import rooms.Room
import tools.confido.refs.eqid
import tools.confido.utils.TOKEN_LEN
import utils.*
import web.location.location

val RoomInviteNoUser = FC<Props> {
    RoomInviteCore {
        form = RoomInviteFormNoUser
    }
}

// TODO(Prin): Update usages to use the redesigned version.
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


    if (!loginRequired) {
        LoginTextInput {
            css {
                marginTop = 12.px
            }
            type = InputType.email
            id = "email-field"
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

        LoginTextInput {
            css {
                marginTop = 1.px
            }
            id = "name-field"
            placeholder = "Name (optional)"
            value = name
            onChange = {
                name = it.target.value
            }
        }

        components.redesign.forms.Button {
            css {
                marginTop = 12.px
                width = 100.pct
                borderRadius = 10.px
            }
            palette = MainPalette.inverted
            onClick = { acceptInvite() }
            +"Start forecasting"
        }

        if (emailError != null) {
            // TODO(Prin): Componentize this.
            ReactHTML.span {
                css {
                    marginTop = 20.px
                    marginBottom = 5.px
                    padding = Padding(10.px, 12.px)
                    textAlign = TextAlign.center
                    backgroundColor = Color("#a327d7")  // TODO(Prin): Use palette.
                    color = Color("#FFFFFF")
                    borderWidth = 0.px
                    borderRadius = 5.px
                    width = 100.pct

                    fontSize = 15.px
                    lineHeight = 18.px
                    fontFamily = FontFamily.sansSerif
                }
                +emailError!!
            }
        }
    } else {
        // TODO(Prin): Rename back to just LoginForm.
        LoginFormRedesigned {
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

            // TODO: The logo can't be here, because when the LoginForm is embedded, it's there twice. :thinking:
            LogoWithText {
                css {
                    marginTop = 60.px
                    marginBottom = 30.px
                }
            }

            if (inviteStatus?.valid == true) {
                p {
                    css {
                        padding = Padding(0.px, 12.px)
                        textAlign = TextAlign.center
                        color = TextPalette.white.color
                        fontSize = 14.px
                        fontWeight = integer(400)
                    }
                    +"You have been invited to room "
                    b {
                        +"${inviteStatus?.roomName}. "
                    }
                    +"Use your email to log in so that you can come back later."
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
