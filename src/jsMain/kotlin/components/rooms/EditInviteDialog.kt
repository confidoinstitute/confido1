package components.rooms

import Client
import components.AppStateContext
import components.showError
import hooks.useCoroutineLock
import io.ktor.http.*
import mui.material.*
import mui.system.sx
import payloads.requests.CreateNewInvite
import payloads.requests.DeleteInvite
import react.*
import react.dom.onChange
import rooms.*
import tools.confido.state.FeatureFlag
import tools.confido.state.appConfig
import users.UserType
import utils.eventValue
import utils.themed

external interface DeleteInviteConfirmationProps : Props {
    var running: Boolean
    var hasUsers: Boolean
    var onDelete: ((Boolean) -> Unit)?
}

val DeleteInviteConfirmation = FC<DeleteInviteConfirmationProps> { props ->
    var open by useState(false)
    Button {
        disabled = props.running
        onClick = {if (props.hasUsers) open = true else props.onDelete?.invoke(false)}
        color = ButtonColor.error
        +"Delete"
    }

    Dialog {
        this.open = open
        onClose = { _, _ -> open = false }
        DialogTitle {
            +"Delete invitation link"
        }
        DialogContent {
            DialogContentText {
                +"There are members who joined this room via this invitation link. By deleting it, you must choose whether these members are kept and become permanent members or are also removed."
            }
        }
        DialogActions {
            Button {
                onClick = {props.onDelete?.invoke(true); open = false}
                color = ButtonColor.success
                +"Keep members"
            }
            Button {
                onClick = {props.onDelete?.invoke(false); open = false}
                color = ButtonColor.error
                +"Remove members"
            }
            Button {
                onClick = {open = false}
                +"Cancel"
            }
        }
    }
}

external interface EditInviteDialogProps : Props {
    var invite: InviteLink?
    var hasUsers: Boolean
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
    var targetUserType by useState(i?.targetUserType ?: UserType.GUEST)
    var requireNickname by useState(i?.requireNickname ?: false)
    var preventDuplicateNicknames by useState(i?.preventDuplicateNicknames ?: false)
    var linkState by useState(i?.state ?: InviteLinkState.ENABLED)

    val htmlId = useId()

    val submit = useCoroutineLock()
    val delete = useCoroutineLock()

    fun submitInviteLink() = submit {
        if (i == null) {
            val invite = CreateNewInvite(
                description = description,
                role = role,
                anonymous = anonymous,
                targetUserType = targetUserType,
                requireNickname = requireNickname,
                preventDuplicateNicknames = preventDuplicateNicknames
            )
            Client.sendData("${room.urlPrefix}/invites/create", invite, onError = {showError(it)}) {
                props.onClose?.invoke()
            }
        } else {
            val invite = i.copy(
                description = description,
                role = role,
                allowAnonymous = anonymous,
                targetUserType = targetUserType,
                requireNickname = requireNickname,
                preventDuplicateNicknames = preventDuplicateNicknames,
                state = linkState,
            )
            Client.sendData("${room.urlPrefix}/invites/edit", invite, onError = {showError(it)}) {
                props.onClose?.invoke()
            }
        }
    }

    fun deleteInviteLink(keepMembers: Boolean) = delete {
        if (i == null) return@delete
            Client.sendData("${room.urlPrefix}/invite", DeleteInvite(i.id, keepMembers), method = HttpMethod.Delete, onError = {showError(it)}) { }
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
                    // We cannot provide viewer and forecaster here, as we are potentially inviting non-members.
                    // They would not be able to see the users of the organization in the moderation
                    // interface because of the censorship. See also: RoomRole.isAvailableForGuests
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
                FormLabel {
                    +"User type"
                }
                RadioGroup {
                    value = targetUserType.toString()
                    onChange = { _, value ->
                        targetUserType = when(value) {
                            "MEMBER" -> UserType.MEMBER
                            "GUEST" -> UserType.GUEST
                            else -> error("This cannot happen!")
                        }
                    }
                    FormControlLabel {
                        label = ReactNode("Full member (access to all workspace features)")
                        value = "MEMBER"
                        control = Radio.create {}
                    }
                    FormControlLabel {
                        label = ReactNode("Guest (access limited to this room)")
                        value = "GUEST"
                        control = Radio.create {}
                    }
                }
            }

            FormControl {
                sx {
                    paddingTop = themed(2)
                    paddingBottom = themed(2)
                }
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
                FormControlLabel {
                    label = ReactNode("Require nickname")
                    control = Checkbox.create {
                        checked = requireNickname
                        onChange = { _, value -> requireNickname = value }
                    }
                }
                FormControlLabel {
                    label = ReactNode(
                        if (targetUserType == UserType.MEMBER)
                            "Prevent duplicate nicknames across workspace"
                        else "Prevent duplicate nicknames within room"
                    )
                    control = Checkbox.create {
                        checked = preventDuplicateNicknames
                        onChange = { _, value -> preventDuplicateNicknames = value }
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
            if (i != null)
                DeleteInviteConfirmation {
                    running = delete.running
                    hasUsers = props.hasUsers
                    onDelete = { deleteInviteLink(it); props.onClose?.invoke() }
                }
            Button {
                onClick = {props.onClose?.invoke()}
                +"Cancel"
            }
            Button {
                onClick = {submitInviteLink()}
                disabled = stale || submit.running
                if (i != null) +"Edit" else +"Add"
            }
        }
    }
}
