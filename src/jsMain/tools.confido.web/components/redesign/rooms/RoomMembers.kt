package tools.confido.web.components.redesign.rooms

import tools.confido.web.Client
import web.window.window
import tools.confido.web.components.usePermission
import tools.confido.web.components.useIsAdmin
import tools.confido.web.components.useMyUser
import tools.confido.web.components.useDisconnected
import tools.confido.web.components.redesign.ConfirmDialog
import tools.confido.web.components.redesign.InviteLinkIcon
import tools.confido.web.components.redesign.NavMenuIcon
import tools.confido.web.components.redesign.basic.Stack
import tools.confido.web.components.redesign.basic.TextPalette
import tools.confido.web.components.redesign.basic.sansSerif
import tools.confido.web.components.redesign.basic.showError
import tools.confido.web.components.redesign.forms.FormDivider
import tools.confido.web.components.redesign.forms.IconButton
import tools.confido.web.components.redesign.forms.Select
import tools.confido.web.components.redesign.layout.LayoutMode
import tools.confido.web.components.redesign.layout.LayoutModeContext
import tools.confido.web.components.redesign.rooms.dialog.AddMemberDialog
import tools.confido.web.components.redesign.rooms.dialog.EditInviteDialog
import tools.confido.web.components.redesign.rooms.dialog.InvitationQuickSettingsDialog
import tools.confido.web.components.redesign.rooms.dialog.MemberQuickSettingsDialog
import web.cssom.*
import emotion.react.css
import tools.confido.web.hooks.useCoroutineLock
import tools.confido.web.hooks.useEditDialog
import tools.confido.web.hooks.useSubscription
import io.ktor.client.request.*
import io.ktor.http.*
import tools.confido.payloads.requests.AddedExistingMember
import tools.confido.payloads.requests.AddedMember
import tools.confido.payloads.requests.DeleteInvite
import tools.confido.payloads.responses.MembershipPayload
import tools.confido.payloads.responses.WSData
import react.FC
import react.Props
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.option
import react.use
import react.useState
import tools.confido.rooms.*
import tools.confido.api.Api
import tools.confido.extensions.ClientExtension
import tools.confido.refs.ref
import tools.confido.users.User
import tools.confido.state.Topics
import tools.confido.state.appConfig
import tools.confido.utils.pluralize
import tools.confido.utils.stringToColor
import tools.confido.web.utils.runCoroutine
import web.clipboard.writeText
import web.location.location
import web.navigator.navigator
import web.window.WindowTarget
import web.window._blank

// Pure (takes the viewer explicitly): also called from event handlers, where the
// use()-based accessors are off-limits — capture useMyUser() during render.
fun canChangeRole(myUser: User?, room: Room, role: RoomRole) =
    canChangeRole(room.userRole(myUser), role)

val RoomMembers = FC<Props> {
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
        if (usePermission(room, RoomPermission.MANAGE_MEMBERS)) {
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
    val room = use(RoomContext)!!
    val member = props.membership
    // Granted only to MANAGE_MEMBERS viewers — management actions need it.
    val userRef = member.user.userId

    var dialogOpen by useState(false)
    var confirmRole by useState<RoomRole?>(null)
    val myUser = useMyUser()  // captured for the role-change handler

    fun memberRoleChange(role: RoomRole) = runCoroutine {
        val ref = userRef ?: return@runCoroutine
        Client.call(Api.roomMemberAdd(room.ref), AddedExistingMember(ref, role))
    }
    val canChangeSelf = !member.user.isMyself || useIsAdmin()

    fun memberDelete() = runCoroutine {
        val ref = userRef ?: return@runCoroutine
        Client.call(Api.roomMemberDelete(room.ref, ref))
    }

    MemberQuickSettingsDialog {
        open = dialogOpen
        name = member.user.displayName ?: "Anonymous user"
        hasEmail = member.email != null
        canDelete = canChangeSelf && usePermission(room, RoomPermission.MANAGE_MEMBERS) == true
        onClose = { dialogOpen = false }
        onMail = { window.open("mailto:${member.email}", WindowTarget._blank) }
        onDelete = ::memberDelete
    }

    confirmRole?.let { role ->
        ConfirmDialog {
            open = true
            title = "Promote to ${role.name}?"
            confirmText = "Promote"
            onClose = { confirmRole = null }
            onConfirm = { memberRoleChange(role); confirmRole = null }
            +"${member.user.displayName ?: "Anonymous user"} will get the ${role.name} role, "
            +"which includes all your permissions — they will be able to change your role too."
        }
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
            disabled = props.disabled || !canChangeSelf || userRef == null
            onChange = { role ->
                // Promoting someone to your own tier hands them symmetric power
                // (they can change your role too) — confirm before committing.
                if (grantsPeerPowers(room.userRole(myUser), role)) confirmRole = role
                else memberRoleChange(role)
            }
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
    val room = use(RoomContext)!!

    val active = props.invitation.canJoin && props.invitation.canAccess

    var dialogOpen by useState(false)
    val delete = useCoroutineLock()
    fun deleteInviteLink(keepMembers: Boolean) = delete {
        val i = props.invitation
        Client.call(Api.inviteDelete(room.ref), DeleteInvite(i.id, keepMembers))
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
    val room = use(RoomContext)!!
    val myUser = useMyUser()  // captured for the onChange handler
    val canChange = canChangeRole(myUser, room, props.value)

    Select {
        value = props.value.id
        disabled = props.disabled || useDisconnected() || !canChange
        onChange = { event ->
            val changedRole = appConfig.roleById(event.target.value)
                ?: error("Unknown role: ${event.target.value}")
            if (canChangeRole(myUser, room, changedRole))
                props.onChange?.invoke(changedRole)
        }
        appConfig.roles.map { role ->
            option {
                value = role.id
                +role.name
                if (!canChangeRole(myUser, room, role) || (props.isGuest && !role.availableToGuests))
                    disabled = true
            }
        }
    }
}
