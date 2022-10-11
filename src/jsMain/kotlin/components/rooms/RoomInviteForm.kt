package components.rooms

import components.AppStateContext
import csstype.*
import emotion.react.css
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import payloads.requests.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.onChange
import react.router.useParams
import payloads.requests.CheckInvite
import payloads.responses.InviteStatus
import react.router.useNavigate
import utils.eventValue
import utils.themed
import kotlin.coroutines.EmptyCoroutineContext

val RoomInviteForm = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    var name by useState("")
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
        val check = CheckInvite(roomId, inviteToken)
        CoroutineScope(EmptyCoroutineContext).launch {
            val result: InviteStatus = Client.postDataAndReceive("/invite/check_status", check)
            inviteStatus = result
        }
    }

    Backdrop {
        this.open = inviteStatus == null
        this.sx { this.zIndex = integer(42) }
        CircularProgress {}
    }

    if (inviteStatus != null) {
        Paper {
            sx {
                marginTop = themed(2)
                padding = themed(2)
            }
            if (inviteStatus?.valid == true) {
                Typography {
                    variant = TypographyVariant.body1
                    +"You have been invited to room ${inviteStatus?.roomName}"
                }
                div {
                    css {
                        marginTop = 5.px
                        display = Display.flex
                        alignItems = AlignItems.flexEnd
                    }
                    if (appState.session.user == null) {
                        TextField {
                            variant = FormControlVariant.standard
                            id = "name-field"
                            label = ReactNode("Name")
                            value = name
                            disabled = stale
                            onChange = {
                                name = it.eventValue()
                            }
                        }
                        Button {
                            onClick = {
                                // For now, we leave email empty.
                                Client.postData(
                                    "/invite/accept_newuser",
                                    AcceptInviteAndCreateUser(roomId, inviteToken, name, null)
                                ).invokeOnCompletion {
                                    navigate("/room/${roomId}")
                                }
                            }
                            disabled = stale
                            +"Set name"
                        }
                    } else {
                        Button {
                            onClick = {
                                Client.postData("/invite/accept", AcceptInvite(roomId, inviteToken)).invokeOnCompletion {
                                    navigate("/room/${roomId}")
                                }
                            }
                            disabled = stale
                            +"Accept invite"
                        }
                    }
                }
            } else {
                Typography {
                    variant = TypographyVariant.body1
                    +"This invite is not valid. It may have been disabled or it has expired."
                }
            }
        }
    }
}
