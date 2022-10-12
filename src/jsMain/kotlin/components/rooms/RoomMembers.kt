package components.rooms

import components.AppStateContext
import components.UserAvatar
import csstype.px
import hooks.useDebounce
import icons.*
import kotlinx.browser.window
import kotlinx.js.timers.setTimeout
import react.*
import mui.material.*
import mui.system.sx
import payloads.requests.AddMember
import rooms.*
import tools.confido.refs.deref
import tools.confido.refs.eqid
import tools.confido.state.SentState
import tools.confido.utils.randomString
import utils.themed

val RoomMembers = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val canManage = appState.hasPermission(room, RoomPermission.MANAGE_MEMBERS)

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
    val invitations = room.inviteLinks.associateWith { groupedMembership[it.id] }

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


    if (appState.hasPermission(room, RoomPermission.MANAGE_MEMBERS)) {
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
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    var copyShown by useState(false)
    useDebounce(3000, copyShown) {
        if (copyShown)
            copyShown = false
    }

    val active = props.invitation.canJoin && props.invitation.canAccess

    ListItem {
        key = props.invitation.id
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
        if (props.invitation.token.isNotEmpty()) {
            Tooltip {
                title = ReactNode(if (copyShown) "Link copied!" else "Copy invitation link")
                onOpen = {copyShown = false}
                arrow = true
                IconButton {
                    ContentCopyIcon {}
                    disabled = !active
                    onClick = {
                        val url = props.invitation.link(window.location.origin, room)
                        window.navigator.clipboard.writeText(url)
                        copyShown = true
                    }
                }
            }
            // TODO QR code
            IconButton {
                disabled = stale
                disabled = !active
                QrCode {}
            }
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
                    this.disabled = !props.invitation.canAccess
                }
            }
        }
    }
}

fun canChangeRole(appState: SentState, room: Room, role: RoomRole) =
    canChangeRole(room.userRole(appState.session.user), role)

external interface MemberRoleSelectProps : Props {
    var value: RoomRole
    var ownerSelectable: Boolean
    var disabled: Boolean
    var onChange: ((RoomRole) -> Unit)?
}

val MemberRoleSelect = FC<MemberRoleSelectProps> {props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    FormControl {
        sx {
            width = 125.px
        }
        val select: FC<SelectProps<String>> = Select
        select {
            this.size = Size.small
            value = props.value.id
            disabled = props.disabled
            onChange = { event, _ ->
                val changedRole = when(event.target.value) {
                    "viewer" -> Viewer
                    "forecaster" -> Forecaster
                    "moderator" -> Moderator
                    "owner" -> Owner
                    else -> error("This role does not exist")
                }
                if (canChangeRole(appState, room, changedRole))
                    props.onChange?.invoke(changedRole)
            }
            // TODO a global list, preferably near room membership definition?
            listOf(Viewer, Forecaster, Moderator, Owner).map { role ->
                if (canChangeRole(appState, room, role))
                    MenuItem {
                        value = role.id
                        +role.name
                    }
            }
        }
    }
}

external interface RoomMemberProps : Props {
    var disabled: Boolean
    var membership: RoomMembership
}

val RoomMember = FC<RoomMemberProps> {props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    val membership = props.membership
    val user = membership.user.deref() ?: return@FC

    fun canChangeSelf() =
        (!(user eqid appState.session.user) || appState.isAdmin())

    fun memberRoleChange(role: RoomRole) {
        Client.postData("/rooms/${room.id}/members/add", AddMember(membership.user, role))
    }

    ListItem {
        disabled = props.disabled
        ListItemAvatar {
            UserAvatar {
                this.user = user
            }
        }
        ListItemText {
            primary = ReactNode(user.nick ?: "Anonymous")
            user.email?.let {
                secondary = ReactNode(it)
            }
        }
        if (canChangeRole(appState, room, membership.role) && canChangeSelf()) {
            MemberRoleSelect {
                value = membership.role
                onChange = ::memberRoleChange
                disabled = stale || props.disabled
            }
        } else {
            Typography {
                +membership.role.name
            }
        }
    }
}