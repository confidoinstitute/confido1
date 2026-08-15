package components.redesign.rooms

import web.window.window
import components.AppStateContext
import components.redesign.InviteLinkIcon
import components.redesign.NavMenuIcon
import components.redesign.basic.Stack
import components.redesign.basic.TextPalette
import components.redesign.basic.sansSerif
import components.redesign.basic.showError
import components.redesign.forms.FormDivider
import components.redesign.forms.IconButton
import components.redesign.forms.Select
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.rooms.dialog.AddMemberDialog
import components.redesign.rooms.dialog.EditInviteDialog
import components.redesign.rooms.dialog.InvitationQuickSettingsDialog
import components.redesign.rooms.dialog.MemberQuickSettingsDialog
import web.cssom.*
import emotion.react.css
import hooks.useCoroutineLock
import hooks.useEditDialog
import hooks.useSubscription
import io.ktor.client.request.*
import io.ktor.http.*
import payloads.requests.AddedExistingMember
import payloads.requests.AddedMember
import payloads.requests.DeleteInvite
import payloads.responses.MembershipPayload
import payloads.responses.WSData
import react.FC
import react.Props
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.option
import react.use
import react.useState
import rooms.*
import tools.confido.extensions.ClientExtension
import tools.confido.refs.ref
import tools.confido.state.ClientState
import tools.confido.state.Topics
import tools.confido.state.appConfig
import tools.confido.utils.pluralize
import tools.confido.utils.stringToColor
import utils.runCoroutine
import web.clipboard.writeText
import web.location.location
import web.navigator.navigator
import web.window.WindowTarget
import web.window._blank

fun canChangeRole(appState: ClientState, room: Room, role: RoomRole) =
    canChangeRole(room.userRole(appState.user), role)

val RoomMembers = FC<Props> {
    val (appState, stale) = use(AppStateContext)!!
    val room = use(RoomContext)!!
    val layoutMode = use(LayoutModeContext)!!

    val membersWS = useSubscription(Topics.roomMembers(room.ref))
    val allMembers = (membersWS as? WSData)?.data ?: emptyList()

    // Separate implied (inherited) members from direct members
    val impliedMembers = allMembers.filter { it.implied }
    val directMembers = allMembers.filter { !it.implied }

    val groupedMembership = directMembers.groupBy {
        it.invitedVia ?: if (it.isProperUser) "internal" else "guest"
    }
    val invitations = room.inviteLinks.associateWith { groupedMembership[it.id] }

    val editInviteLinkDialog = useEditDialog(EditInviteDialog)

    var addMemberOpen by useState(false)
    AddMemberDialog {
        open = addMemberOpen
        onClose = {addMemberOpen = false}
    }

    RoomHeader {
        if (appState.hasPermission(room, RoomPermission.MANAGE_MEMBERS)) {
            RoomHeaderButton {
                +"Create an invitation link"
                onClick = { editInviteLinkDialog(null) }
            }
            RoomHeaderButton {
                +"Add member"
                onClick = { addMemberOpen = true }
            }
        }
    }

    val bgColor = if (layoutMode == LayoutMode.PHONE) { Color("#FFFFFF") } else { null }

    Stack {
        css {
            flexGrow = number(1.0)
            width = layoutMode.contentWidth
            marginLeft = Auto.auto
            marginRight = Auto.auto

        }
        groupedMembership["internal"]?.let {
            DividerHeading { text = "Users from this organization" }
            Stack {
                css { gap = 10.px; padding = 15.px; backgroundColor = bgColor }
                it.map {
                    RoomMember {
                        key = it.rowKey
                        disabled = false
                        membership = it
                    }
                }
            }
        }
        groupedMembership["guest"]?.let {
            DividerHeading { text = "Temporary guests" }
            Stack {
                css { gap = 10.px }
                css { gap = 10.px; padding = 15.px; backgroundColor = bgColor }
                it.map {
                    RoomMember {
                        key = it.rowKey
                        disabled = false
                        membership = it
                    }
                }
            }
        }
        if (invitations.isNotEmpty()) {
            DividerHeading { text = "Invitation links" }
            invitations.entries.sortedBy { it.key.createdAt }.map { (invitation, maybeMembers) ->
                Stack {
                    css { gap = 10.px; padding = 15.px; backgroundColor = bgColor }
                    InvitationMembers {
                        key = "invitation__" + invitation.token
                        this.invitation = invitation
                        this.members = maybeMembers
                        this.onEditDialog = { editInviteLinkDialog(it) }
                    }
                }
            }
        }
        if (impliedMembers.isNotEmpty()) {
            // Group implied members by their source (impliedFrom)
            val groupedImplied = impliedMembers.groupBy { it.impliedFrom ?: "Inherited" }
            groupedImplied.forEach { (source, members) ->
                DividerHeading { text = "Inherited from $source" }
                Stack {
                    css { gap = 10.px; padding = 15.px; backgroundColor = bgColor; opacity = number(0.7) }
                    members.map {
                        RoomMember {
                            key = it.rowKey
                            disabled = true
                            membership = it
                        }
                    }
                }
            }
        }
        div {
            css {
                flexGrow = number(1.0)
                backgroundColor = bgColor
            }
        }
    }
}

external interface RoomMemberProps : Props {
    var disabled: Boolean
    var membership: MembershipPayload
}

val RoomMember = FC<RoomMemberProps> {props ->
    val (appState, stale) = use(AppStateContext)!!
    val room = use(RoomContext)!!
    val member = props.membership
    // Granted only to MANAGE_MEMBERS viewers — management actions need it.
    val userRef = member.user.userId

    var dialogOpen by useState(false)

    fun memberRoleChange(role: RoomRole) = runCoroutine {
        val ref = userRef ?: return@runCoroutine
        Client.sendData("${room.urlPrefix}/members/add", AddedExistingMember(ref, role) as AddedMember, onError = { showError(it)}) {}
    }
    fun canChangeSelf() = (!member.user.isMyself || appState.isAdmin())

    fun memberDelete() = runCoroutine {
        val ref = userRef ?: return@runCoroutine
        Client.httpClient.delete("${room.urlPrefix}/members/${ref.id}")
    }

    MemberQuickSettingsDialog {
        open = dialogOpen
        name = member.user.displayName ?: "Anonymous user"
        hasEmail = member.email != null
        canDelete = canChangeSelf() && appState.hasPermission(room, RoomPermission.MANAGE_MEMBERS) == true
        onClose = { dialogOpen = false }
        onMail = { window.open("mailto:${member.email}", WindowTarget._blank) }
        onDelete = ::memberDelete
    }

    Stack {
        direction = FlexDirection.row
        css {
            width = 100.pct
            alignItems = AlignItems.center
            gap = 10.px
        }
        ReactHTML.div {
            css {
                width = 32.px
                height = 32.px
                borderRadius = 50.pct
                backgroundColor = Color(member.user.color ?: stringToColor(member.rowKey))
                flexShrink = number(0.0)
            }
        }
        Stack {
            css {
                flexGrow = number(1.0)
                flexShrink = number(1.0)
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
                whiteSpace = WhiteSpace.nowrap
            }
            ReactHTML.div {
                css {
                    fontFamily = sansSerif
                    fontWeight = integer(600)
                    fontSize = 12.px
                    lineHeight = 15.px
                }
                +(member.user.displayName ?: "Anonymous user")
            }
            ReactHTML.div {
                css {
                    fontFamily = sansSerif
                    fontSize = 12.px
                    lineHeight = 15.px
                    color = rgb(0,0,0,0.5)
                }
                +(member.email ?: "")
            }
            ClientExtension.forEach { it.roomMemberExtra(room, member, this) }
        }
        MemberRoleSelect {
            value = member.role
            isGuest = !member.isProperUser
            disabled = props.disabled || !canChangeSelf() || userRef == null
            onChange = ::memberRoleChange
        }
        IconButton {
            this.palette = TextPalette.black
            onClick = {
                dialogOpen = true
            }
            NavMenuIcon {}
        }
    }
}
external interface InvitationMembersProps : Props {
    var invitation: InviteLink
    var members: List<MembershipPayload>?
    var canManage: Boolean
    var onEditDialog: ((InviteLink) -> Unit)?
}

val InvitationMembers = FC<InvitationMembersProps> { props ->
    val (appState, stale) = use(AppStateContext)!!
    val room = use(RoomContext)!!

    val active = props.invitation.canJoin && props.invitation.canAccess

    var dialogOpen by useState(false)
    val delete = useCoroutineLock()
    fun deleteInviteLink(keepMembers: Boolean) = delete {
        val i = props.invitation
        Client.sendData("${room.urlPrefix}/invite", DeleteInvite(i.id, keepMembers), method = HttpMethod.Delete, onError = {showError(it)}) { }
    }

    InvitationQuickSettingsDialog {
        link = props.invitation
        open = dialogOpen
        hasUsers = props.members?.isNotEmpty() == true
        onClose = { dialogOpen = false }
        onCopy = {
            val url = props.invitation.link(location.origin, room)
            runCoroutine {  navigator.clipboard.writeText(url) }
        }
        onEdit = { props.onEditDialog?.invoke(props.invitation) }
        onDelete = { deleteInviteLink(it) }
    }

    Stack {
        direction = FlexDirection.column
        css {
        }
        Stack {
            direction = FlexDirection.row
            css {
                width = 100.pct
                height = 40.px
                alignItems = AlignItems.center
                gap = 10.px
            }
            ReactHTML.div {
                css {
                    width = 32.px
                    height = 32.px
                    borderRadius = 50.pct
                    backgroundColor = Color("#F61E4B")
                    flexShrink = number(0.0)
                }
                if (active)
                    InviteLinkIcon {}
            }
            Stack {
                css {
                    flexGrow = number(1.0)
                    flexShrink = number(1.0)
                    overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    whiteSpace = WhiteSpace.nowrap
                }
                ReactHTML.div {
                    css {
                        fontFamily = sansSerif
                        fontWeight = integer(600)
                        fontSize = 12.px
                        lineHeight = 15.px
                    }
                    +props.invitation.description
                    if (!active)
                        +" (inactive)"
                }
                ReactHTML.div {
                    css {
                        fontFamily = sansSerif
                        fontSize = 12.px
                        lineHeight = 15.px
                        color = rgb(0, 0, 0, 0.5)
                    }
                    val count = props.members?.size ?: 0
                    +pluralize("member", count, includeCount = true)
                    if (!props.invitation.canAccess) {
                        +" (cannot access)"
                    }
                }
            }
            IconButton {
                this.palette = TextPalette.black
                onClick = { dialogOpen = true }
                NavMenuIcon {}
            }
        }
        props.members?.let { justMembers ->
            Stack {
                css {
                    margin = Margin(5.px, 0.px, 10.px, 15.px)
                    borderLeft = Border(2.px, LineStyle.solid, Color("#DADADA"))
                    padding = Padding(5.px, 0.px, 5.px, 25.px)
                    gap = 10.px
                }
                justMembers.map { membership ->
                    RoomMember {
                        key = membership.rowKey
                        this.membership = membership
                        this.disabled = !props.invitation.canAccess
                    }
                }
            }
        }
    }
}

external interface MemberRoleSelectProps : Props {
    var value: RoomRole
    var isGuest: Boolean
    var disabled: Boolean
    var onChange: ((RoomRole) -> Unit)?
}

val MemberRoleSelect = FC<MemberRoleSelectProps> {props ->
    val (appState, stale) = use(AppStateContext)!!
    val room = use(RoomContext)!!
    val canChange = canChangeRole(appState, room, props.value)

    Select {
        value = props.value.id
        disabled = props.disabled || stale || !canChange
        onChange = { event ->
            val changedRole = appConfig.roleById(event.target.value)
                ?: error("Unknown role: ${event.target.value}")
            if (canChangeRole(appState, room, changedRole))
                props.onChange?.invoke(changedRole)
        }
        appConfig.roles.map { role ->
            option {
                value = role.id
                +role.name
                if (!canChangeRole(appState, room, role) || (props.isGuest && !role.availableToGuests))
                    disabled = true
            }
        }
    }
}
