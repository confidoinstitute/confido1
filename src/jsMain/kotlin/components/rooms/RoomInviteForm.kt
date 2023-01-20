package components.rooms

import components.AppStateContext
import components.RouterLink
import components.nouser.LoginForm
import csstype.*
import io.ktor.client.call.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.js.get
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
import utils.*

// TODO Split this huge component into nouser and user variants

val RoomInviteForm = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val user = appState.session.user
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
        runCoroutine {
            Client.sendData("/rooms/${roomId}/invite/check", check, onError = {}) {
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
        val missingRequiredEmail = user != null && user.email == null && !inviteStatus!!.allowAnonymous
        if (missingRequiredEmail) {
            Container {
                maxWidth = byTheme("md")
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

                if (user == null) {
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
                                        runCoroutine {
                                            Client.sendData("/rooms/$roomId/invite/accept_newuser",
                                                AcceptInviteAndCreateUser(
                                                    inviteToken,
                                                    name.trim().ifEmpty { null },
                                                    userMail
                                                ),
                                                onError = {}
                                            ) {
                                                if (body()) {
                                                    // We need to log in.
                                                    loginRequired = true
                                                } else {
                                                    navigate("/room/${roomId}")
                                                }
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
                        variant = ButtonVariant.contained
                        onClick = { runCoroutine {
                            Client.sendData("/rooms/$roomId/invite/accept", AcceptInvite(inviteToken), onError = {}) {
                                navigate("/room/${roomId}")
                            }
                        } }
                        disabled = stale || missingRequiredEmail
                        +"Start forecasting"
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
