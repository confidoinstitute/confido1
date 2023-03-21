package components.redesign.rooms

import Client
import components.*
import components.redesign.basic.*
import components.redesign.forms.*
import csstype.*
import emotion.react.*
import hooks.*
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.js.*
import payloads.requests.*
import payloads.responses.*
import react.*
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.code
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.router.*
import react.router.dom.*
import tools.confido.refs.*
import tools.confido.utils.*
import utils.*
import web.location.*

val RoomInviteLoggedIn = FC<Props> {
    Stack {
        css {
            alignItems = AlignItems.center
        }
        Stack {
            css {
                marginTop = 64.px
                maxWidth = 400.px
                alignItems = AlignItems.center
            }
            RoomInviteCore {
                topAlert = MissingEmailConditionalAlert
                form = RoomInviteFormLoggedIn
                palette = MainPalette.default
            }
        }
    }
}

external interface InvalidInviteAlertProps: PropsWithPalette<PaletteWithText> {
    var tooShort: Boolean
}

private val InvalidInviteAlert = FC<InvalidInviteAlertProps> { props->
    val palette = props.palette ?: MainPalette.login

    span {
        css {
            padding = Padding(0.px, 12.px)
            textAlign = TextAlign.center
            color = palette.text.color
            fontSize = 14.px
            fontFamily = sansSerif
            fontWeight = integer(400)
        }
        p {
            b {
                +"Invalid invitation link"
            }
        }
        p {
            +"The invitation link "
            code { +location.href }

            if (props.tooShort) {
                +" appears to be incomplete. Please ensure that you have copied the entire link."
            } else {
                +" is not valid. Please ensure you have copied it correctly."
                +" It may also have been disabled or have expired."
            }
        }
    }
}

external interface RoomInviteFormProps : Props {
    var roomId: String
    var roomName: String
    var inviteToken: String
    var allowAnonymous: Boolean
}


private val RoomInviteFormLoggedIn = FC<RoomInviteFormProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val navigate = useNavigate()

    val user = appState.session.user
    val missingRequiredEmail = user != null && user.email == null && !props.allowAnonymous

    useEffectOnce {
        val alreadyAccepted = appState.rooms[props.roomId]?.let { room ->
            room.members.any { membership -> membership.user eqid appState.session.user }
        } ?: false

        if (alreadyAccepted) {
            navigate(roomUrl(props.roomId), jso {
                replace = true
            })
        }
    }

    RoomNavbar {
        palette = roomPalette(props.roomId)
        navigateBack = "/"
    }

    p {
        css {
            padding = Padding(0.px, 12.px)
            textAlign = TextAlign.center
            color = TextPalette.black.color
            fontSize = 14.px
            fontWeight = integer(400)
        }
        +"You have been invited to room "
        b {
            +"${props.roomName}."
        }
    }

    Button {
        css {
            width = 100.pct
        }
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
            h3 {
                +"No email is set!"
            }
            +"This invite requires you to set an email before accepting. You can do so in "
            Link {
                css {
                    color = Globals.inherit
                }
                to = "/profile"
                +"user settings"
            }
            +"."
        }
    }
}

external interface RoomInviteCoreProps : PropsWithPalette<PaletteWithText> {
    var topAlert: ComponentType<RoomInviteAlertProps>?
    var form: ComponentType<RoomInviteFormProps>
}

internal val RoomInviteCore = FC<RoomInviteCoreProps> { props ->
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

    useDocumentTitle("Invitation")

    Backdrop {
        this.`in` = inviteStatus == null
    }

    if (inviteStatus != null) {
        +props.topAlert?.create {
            this.allowAnonymous = inviteStatus!!.allowAnonymous
        }

        if (inviteStatus?.valid == true) {
            props.form {
                this.roomId = inviteStatus!!.roomRef!!.id
                this.roomName = inviteStatus!!.roomName!!
                this.inviteToken = inviteToken
                this.allowAnonymous = inviteStatus!!.allowAnonymous
            }
        } else {
            InvalidInviteAlert {
                tooShort = inviteToken.length < TOKEN_LEN && !POTENTIAL_SHORTLINK_RE.matches(inviteToken)
                palette = props.palette
            }
        }
    }
}
