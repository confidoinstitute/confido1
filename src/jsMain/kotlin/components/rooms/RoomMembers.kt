package components.rooms

import components.AppStateContext
import components.UserAvatar
import components.questions.EditQuestionDialog
import hooks.useDebounce
import icons.*
import kotlinx.browser.window
import kotlinx.js.Object
import kotlinx.js.timers.Timeout
import kotlinx.js.timers.clearTimeout
import kotlinx.js.timers.setTimeout
import react.*
import mui.material.*
import mui.system.sx
import react.dom.html.ReactHTML.li
import rooms.InviteLink
import rooms.RoomMembership
import rooms.RoomPermission
import tools.confido.question.Question
import tools.confido.utils.randomString
import users.User
import users.UserType
import utils.jsObject
import utils.themed

val RoomMembers = FC<Props> {
    val clientAppState = useContext(AppStateContext)
    val state = clientAppState.state
    val stale = clientAppState.stale
    val room = useContext(RoomContext)
    val canManage = state.hasPermission(room, RoomPermission.MANAGE_MEMBERS)

    var editInviteLink by useState<InviteLink?>(null)
    var editInviteLinkKey by useState("")
    var editInviteLinkOpen by useState(false)
    useLayoutEffect(editInviteLinkOpen) {
        if (editInviteLinkOpen)
            editInviteLinkKey = randomString(20)
    }


    EditInviteDialog {
        key = "##editDialog##$editInviteLinkKey"
        invite = editInviteLink
        open = editInviteLinkOpen
        onClose = { editInviteLinkOpen = false }
    }

    if (canManage)
        UserInviteForm {
            key = "__invite_form"
        }

    val groupedMembership = room.members.groupBy {
        it.invitedVia
    }
    val invitations = room.inviteLinks.associateWith { groupedMembership[it] }

    List {
        groupedMembership[null]?.let {
            ListSubheader {
                key = "permanent__label"
                +"Full members"
            }
            it.map {membership ->
                RoomMember {
                    key = "permanent__user__${membership.user.id}"
                    this.membership = membership
                    this.canManage = canManage
                    this.disabled = false
                }
            }
        }
        if (invitations.isNotEmpty()) {
            ListSubheader {
                key = "invitations__label"
                +"Invitation links"
            }

        }
        invitations.entries.sortedBy { it.key?.createdAt }.map {(invitation, maybeMembers) ->
            InvitationMembers {
                key = "invitation__" + invitation.token
                this.invitation = invitation
                this.members = maybeMembers
                this.canManage = canManage
                this.onEditDialog = { editInviteLink = it ; editInviteLinkOpen = true }
            }

        }
    }


    if (state.hasPermission(room, RoomPermission.MANAGE_MEMBERS)) {
        Fragment {
            Button {
                this.key = "##add##"
                this.startIcon = AddIcon.create()
                this.color = ButtonColor.primary
                this.disabled = stale
                onClick = { editInviteLink = null; editInviteLinkOpen = true }
                +"Add invitation link…"
            }
        }
    }
}

external interface InvitationMembersProps : Props {
    var invitation: InviteLink
    var members: List<RoomMembership>?
    var canManage: Boolean
    var onEditDialog: ((InviteLink) -> Unit)?
}

val InvitationMembers = FC<InvitationMembersProps> {props ->
    val stale = useContext(AppStateContext).stale
    val room = useContext(RoomContext)

    var copyShown by useState(false)
    useDebounce(3000, copyShown) {
        if (copyShown)
            copyShown = false
    }

    val active = props.invitation.canJoin && props.invitation.canAccess

    ListItem {
        key = props.invitation.token
        ListItemIcon {
            (if (active) LinkIcon else LinkOffIcon) {}
        }
        ListItemText {
            primary = Fragment.create {
                +props.invitation.description
                if (!active) +" (inactive)"
            }
            secondary = ReactNode("${props.members?.size ?: 0} members")
        }
        Tooltip {
            open = copyShown
            title = ReactNode("Link copied!")
            arrow = true
            IconButton {
                ContentCopyIcon {}
                disabled = !active
                onClick = {
                    val invitePath = "room/${room.id}/invite/${props.invitation.token}"
                    val url = "${window.location.origin}/$invitePath"
                    window.navigator.clipboard.writeText(url)
                    copyShown = true
                }
                onMouseOut = {
                    copyShown = false
                }
            }
        }
        IconButton {
            disabled = stale
            disabled = !active
            QrCode {}
        }
        if (props.canManage) {
            IconButton {
                disabled = stale
                onClick = { props.onEditDialog?.invoke(props.invitation) }
                SettingsIcon {}
            }
        }
    }

    props.members?.let { justMembers ->
        List {
            dense = true
            sx {
                paddingLeft = themed(4)
            }
            ListSubheader {
                +"Members joined via this link"
            }
            justMembers.map { membership ->
                RoomMember {
                    key = membership.user.id
                    this.membership = membership
                    this.canManage = props.canManage
                    this.disabled = !props.invitation.canAccess
                }
            }
        }
    }
}

external interface RoomMemberProps : Props {
    var disabled: Boolean
    var membership: RoomMembership
    var canManage: Boolean
}

val RoomMember = FC<RoomMemberProps> {props ->
    val clientAppState = useContext(AppStateContext)
    val stale = clientAppState.stale

    val membership = props.membership

    ListItem {
        disabled = props.disabled
        ListItemAvatar {
            UserAvatar {
                user = membership.user
            }
        }
        ListItemText {
            primary = ReactNode(membership.user.nick ?: "Anonymous")
            membership.user.email?.let {
                secondary = ReactNode(it)
            }
        }
        if (props.canManage && !props.disabled) {
            FormControl {
                val select: FC<SelectProps<String>> = Select
                select {
                    this.size = Size.small
                    value = membership.role.id
                    disabled = stale
                    onChange = { event, _ ->
                        window.alert("${membership.user} permission to ${event.target.value}")
                    }
                    // TODO a global list, preferably near room membership definition?
                    listOf(
                        "forecaster" to "Forecaster",
                        "moderator" to "Moderator"
                    ).map { (id, name) ->
                        MenuItem {
                            value = id
                            +name
                        }
                    }
                }
            }
        } else {
            Typography {
                +membership.role.name
            }
        }
    }
}