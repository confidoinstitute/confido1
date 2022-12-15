package components.rooms

import Client
import components.AppStateContext
import mui.material.*
import mui.system.sx
import payloads.requests.CreateNewInvite
import react.*
import react.dom.onChange
import rooms.*
import tools.confido.state.FeatureFlag
import tools.confido.state.appConfig
import utils.eventValue
import utils.themed


external interface EditInviteDialogProps : Props {
    var invite: InviteLink?
    var open: Boolean
    var onClose: (() -> Unit)?
}

val EditInviteDialog = FC<EditInviteDialogProps> { props ->
    val i = props.invite
    val room = useContext(RoomContext)
    val stale = useContext(AppStateContext).stale

    // Invitation link values
    var description by useState(i?.description ?: "Shared Invite Link")
    var role by useState(i?.role ?: Forecaster)
    var anonymous by useState(i?.allowAnonymous ?: false)
    var linkState by useState(i?.state ?: InviteLinkState.ENABLED)

    val htmlId = useId()

    fun submitInviteLink() {
        if (i == null) {
            val invite = CreateNewInvite(description, role!!, anonymous)
            Client.postData("/rooms/${room.id}/invites/create", invite)
        } else {
            val invite = i.copy(
                description = description,
                role = role,
                allowAnonymous = anonymous,
                state = linkState,
            )
            Client.postData("/rooms/${room.id}/invites/edit", invite)
        }
        props.onClose?.invoke()
    }

    Dialog {
        open = props.open
        onClose = {_, _ -> props.onClose?.invoke() }
        DialogTitle {
            if (i != null) +"Edit invitation link" else +"Add invitation link"
        }
        DialogContent {
            DialogContentText {
                +"One invitation link can be shared with multiple people. Anyone who has the link will be able to join this room."
            }
            TextField {
                value = description
                label = ReactNode("Description")
                fullWidth = true
                onChange = { description = it.eventValue() }
                margin = FormControlMargin.normal
                helperText = ReactNode("An internal description for moderation purposes.")
            }
            FormControl {
                this.fullWidth = true
                InputLabel {
                    this.id = htmlId +"role_label"
                    +"Member role"
                }
                val select: FC<SelectProps<String>> = Select
                select {
                    this.id = htmlId + "role"
                    labelId = htmlId + "role_label"
                    value = role.id
                    label = ReactNode("Member role")
                    onChange = { event, _ ->
                        role = when(event.target.value) {
                            "viewer" -> Viewer
                            "forecaster" -> Forecaster
                            "question_writer" -> QuestionWriter
                            else -> error("This should not happen!")
                        }
                    }
                    (
                            mapOf("viewer" to "Viewer", "forecaster" to "Forecaster")
                            + if (FeatureFlag.QUESTION_WRITER_ROLE in appConfig.featureFlags)
                                    mapOf("question_writer" to "Question Writer")
                                    else emptyMap()
                    ).map { (value, label) ->
                        MenuItem {
                            this.value = value
                            +label
                        }
                    }
                }
                FormHelperText {
                    +"Any new user that joins via this link will get this role."
                }
            }

            FormControl {
                sx {
                    paddingTop = themed(2)
                    paddingBottom = themed(2)
                }
                // TODO better names
                FormLabel {
                    +"User identification"
                }
                RadioGroup {
                    value = anonymous.toString()
                    onChange = { _, value ->
                        anonymous = when(value) {
                            "true" -> true
                            "false" -> false
                            else -> error("This cannot happen!")
                        }
                    }
                    FormControlLabel {
                        label = ReactNode("Allow anonymous access (using only a nickname)")
                        value = "true"
                        control = Radio.create {}
                    }
                    FormControlLabel {
                        label = ReactNode("Require e-mail")
                        value = "false"
                        control = Radio.create {}
                    }
                }
            }
            FormGroup {
                if (i != null) {
                    FormControlLabel {
                        label = ReactNode("Prevent new users from joining")
                        control = Checkbox.create {
                            checked = linkState != InviteLinkState.ENABLED
                        }
                        onChange = { _, value -> linkState = if(value) InviteLinkState.DISABLED_JOIN else InviteLinkState.ENABLED }
                    }
                    FormControlLabel {
                        label = ReactNode("Prevent already joined users from participating")
                        control = Checkbox.create {
                            checked = linkState == InviteLinkState.DISABLED_FULL
                        }
                        onChange = { _, value -> linkState = if (value) InviteLinkState.DISABLED_FULL else InviteLinkState.DISABLED_JOIN }
                    }
                }
            }

        }
        DialogActions {
            Button {
                onClick = {props.onClose?.invoke()}
                +"Cancel"
            }
            Button {
                onClick = {submitInviteLink()}
                disabled = stale
                if (i != null) +"Edit" else +"Add"
            }
        }
    }
}