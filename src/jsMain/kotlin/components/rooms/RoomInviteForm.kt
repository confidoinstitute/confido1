package components.rooms

import components.AppStateContext
import csstype.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import payloads.requests.*
import react.*
import react.dom.onChange
import react.router.useParams
import payloads.requests.CheckInvite
import payloads.responses.InviteStatus
import react.dom.html.ReactHTML.em
import react.router.useNavigate
import utils.byTheme
import utils.eventValue
import utils.themed
import kotlin.coroutines.EmptyCoroutineContext

val RoomInviteForm = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    var name by useState("")
    var email by useState("")
    val roomId = useParams()["roomID"] ?: run {
        console.error("Missing room id")
        return@FC
    }
    val inviteToken = useParams()["inviteToken"] ?: run {
        console.error("Missing invite token")
        return@FC
    }

    val navigate = useNavigate()

    var inviteStatus by useState<InviteStatus?>(null)

    useEffectOnce {
        val check = CheckInvite(inviteToken)
        CoroutineScope(EmptyCoroutineContext).launch {
            val result: InviteStatus = Client.postDataAndReceive("/rooms/${roomId}/invite/check", check)
            inviteStatus = result
        }
    }

    Backdrop {
        this.open = inviteStatus == null
        this.sx { this.zIndex = integer(42) }
        CircularProgress {}
    }

    if (inviteStatus != null) {
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
                if (appState.session.user == null) {
                    TextField {
                        sx {
                            marginTop = themed(2)
                        }
                        margin = FormControlMargin.normal
                        variant = FormControlVariant.outlined
                        fullWidth = true
                        id = "email-field"
                        label = ReactNode("Email (optional)")
                        value = email
                        disabled = stale
                        onChange = {
                            email = it.eventValue()
                        }
                    }

                    TextField {
                        margin = FormControlMargin.normal
                        variant = FormControlVariant.outlined
                        fullWidth = true
                        id = "name-field"
                        label = ReactNode("Name (optional)")
                        value = name
                        disabled = stale
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
                        onClick = {
                            val userMail = if (email.isNotBlank()) {
                                email.trim()
                            } else {
                                null
                            }
                            // For now, we leave email empty.
                            Client.postData(
                                "/rooms/$roomId/invite/accept_newuser",
                                AcceptInviteAndCreateUser(inviteToken, name.ifEmpty { null }, userMail)
                            ).invokeOnCompletion {
                                navigate("/room/${roomId}")
                            }
                        }
                        disabled = stale
                        +"Start forecasting"
                    }
                } else {
                    // TODO: Consider accepting automatically and navigating away in this case.
                    Button {
                        sx {
                            marginTop = themed(2)
                        }
                        onClick = {
                            Client.postData("/rooms/$roomId/invite/accept", AcceptInvite(inviteToken)).invokeOnCompletion {
                                navigate("/room/${roomId}")
                            }
                        }
                        disabled = stale
                        +"Accept invite"
                    }
                }
            } else {
                Alert {
                    severity = AlertColor.error
                    AlertTitle {
                        +"Invalid invite"
                    }
                    +"This invite is not valid. It may have been disabled or it has expired."
                }
            }
        }
    }
}
