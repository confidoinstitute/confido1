package components.rooms

import components.AppStateContext
import components.nouser.LoginForm
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
import react.dom.html.InputType
import react.dom.html.ReactHTML.em
import react.router.useNavigate
import tools.confido.refs.eqid
import utils.byTheme
import utils.eventValue
import utils.isEmailValid
import utils.themed
import kotlin.coroutines.EmptyCoroutineContext

val RoomInviteForm = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    var name by useState("")
    var email by useState("")
    var emailError by useState<String?>(null)
    val roomId = useParams()["roomID"] ?: run {
        console.error("Missing room id")
        return@FC
    }
    val inviteToken = useParams()["inviteToken"] ?: run {
        console.error("Missing invite token")
        return@FC
    }

    val navigate = useNavigate()

    var loginRequired by useState(false)
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
                    val emailRequired = !inviteStatus!!.allowAnonymous

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
                            disabled = stale
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
                                val userMail = email.trim().ifEmpty { null }
                                val emailValid = userMail?.let { isEmailValid(it) } ?: false

                                if (userMail != null && !emailValid) {
                                    emailError = "This email address is not valid."
                                } else {
                                    if (emailRequired && userMail == null) {
                                        emailError = "An email address is required."
                                    } else {
                                        CoroutineScope(EmptyCoroutineContext).launch {
                                            val userAlreadyExists: Boolean = Client.postDataAndReceive(
                                                "/rooms/$roomId/invite/accept_newuser",
                                                AcceptInviteAndCreateUser(
                                                    inviteToken,
                                                    name.trim().ifEmpty { null },
                                                    userMail
                                                )
                                            )

                                            if (userAlreadyExists) {
                                                // We need to log in.
                                                loginRequired = true
                                            } else {
                                                navigate("/room/${roomId}")
                                            }
                                        }
                                    }
                                }
                            }
                            disabled = stale
                            +"Start forecasting"
                        }
                    } else {
                        LoginForm {
                            prefilledEmail = email
                        }
                    }
                } else {
                    val alreadyAccepted = appState.rooms[roomId]?.let { room ->
                        room.members.any { membership -> membership.user eqid appState.session.user }
                    } ?: false

                    if (alreadyAccepted) {
                        navigate("/room/${roomId}")
                    }

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
