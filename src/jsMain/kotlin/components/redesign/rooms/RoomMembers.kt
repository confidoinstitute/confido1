package components.redesign.rooms

import components.AppStateContext
import components.redesign.NavMenuIcon
import components.redesign.basic.Stack
import components.redesign.basic.TextPalette
import components.redesign.basic.sansSerif
import components.redesign.forms.FormDivider
import components.redesign.forms.FormSection
import components.redesign.forms.IconButton
import components.redesign.forms.Select
import components.rooms.RoomContext
import components.showError
import csstype.*
import emotion.react.css
import payloads.requests.AddedExistingMember
import payloads.requests.AddedMember
import react.FC
import react.Props
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.option
import react.useContext
import rooms.*
import tools.confido.refs.deref
import tools.confido.refs.eqid
import tools.confido.state.FeatureFlag
import tools.confido.state.SentState
import tools.confido.state.appConfig
import utils.runCoroutine
import utils.stringToColor

fun canChangeRole(appState: SentState, room: Room, role: RoomRole) =
    canChangeRole(room.userRole(appState.session.user), role)

val RoomMembers = FC<Props> {
    val room = useContext(RoomContext)

    val groupedMembership = room.members.groupBy {
        it.invitedVia ?: if (it.user.deref()?.type?.isProper()?:false) "internal" else "guest"
    }
    val invitations = room.inviteLinks.associateWith { groupedMembership[it.id] }

    groupedMembership["internal"]?.let {
        FormDivider { +"Users from this organization" }
        Stack {
            css { gap = 10.px; padding = 15.px; backgroundColor = Color("#FFFFFF") }
            it.map {
                RoomMember {
                    disabled = false
                    membership = it
                }
            }
        }
    }
    groupedMembership["guest"]?.let {
        FormDivider {+"Temporary guests" }
        Stack {
            css { gap = 10.px }
            css { gap = 10.px; padding = 15.px; backgroundColor = Color("#FFFFFF") }
            it.map {
                RoomMember {
                    disabled = false
                    membership = it
                }
            }
        }
    }
    if (invitations.isNotEmpty()) {
        FormDivider { +"Invitation links" }
        invitations.entries.sortedBy { it.key.createdAt }.map { (invitation, maybeMembers) ->
            Stack {
                css { gap = 10.px; padding = 15.px; backgroundColor = Color("#FFFFFF") }
                InvitationMembers {
                    key = "invitation__" + invitation.token
                    this.invitation = invitation
                    this.members = maybeMembers
                    //this.onEditDialog = { editInviteLink = it ; editInviteLinkOpen = true }
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
    val user = props.membership.user.deref() ?: return@FC


    fun memberRoleChange(role: RoomRole) = runCoroutine {
        Client.sendData("${room.urlPrefix}/members/add", AddedExistingMember(props.membership.user, role) as AddedMember, onError = { showError?.invoke(it)}) {}
    }
    fun canChangeSelf() =
        (!(user eqid appState.session.user) || appState.isAdmin())

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
                backgroundColor = stringToColor(user.id)
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
                +(user.nick ?: "Anonymous user")
            }
            ReactHTML.div {
                css {
                    fontFamily = sansSerif
                    fontSize = 12.px
                    lineHeight = 15.px
                    color = rgba(0,0,0,0.5)
                }
                +(user.email ?: "")
            }
        }
        MemberRoleSelect {
            value = props.membership.role
            isGuest = !user.type.isProper()
            disabled = props.disabled
            onChange = ::memberRoleChange
        }
        IconButton {
            this.palette = TextPalette.black
            NavMenuIcon {}
        }
    }
}
external interface InvitationMembersProps : Props {
    var invitation: InviteLink
    var members: List<RoomMembership>?
    var canManage: Boolean
    var onEditDialog: ((InviteLink) -> Unit)?
}

val InvitationMembers = FC<InvitationMembersProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    val active = props.invitation.canJoin && props.invitation.canAccess


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
                // TODO link icons
                css {
                    width = 32.px
                    height = 32.px
                    borderRadius = 50.pct
                    backgroundColor = Color("#F61E4B")
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
                    +props.invitation.description
                }
                ReactHTML.div {
                    css {
                        fontFamily = sansSerif
                        fontSize = 12.px
                        lineHeight = 15.px
                        color = rgba(0, 0, 0, 0.5)
                    }
                    // TODO Pluralize
                    +"${props.members?.size ?: 0} members"
                }
            }
            IconButton {
                this.palette = TextPalette.black
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
                        key = membership.user.id
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
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val canChange = canChangeRole(appState, room, props.value)
    Select {
        value = props.value.id
        disabled = props.disabled || stale || !canChange
        onChange = { event ->
            val changedRole = when(event.target.value) {
                "viewer" -> Viewer
                "forecaster" -> Forecaster
                "question_writer" -> QuestionWriter
                "moderator" -> Moderator
                "owner" -> Owner
                else -> error("This role does not exist")
            }
            if (canChangeRole(appState, room, changedRole))
                props.onChange?.invoke(changedRole)
        }
        val qw = if (FeatureFlag.QUESTION_WRITER_ROLE in appConfig.featureFlags) arrayOf(QuestionWriter) else emptyArray()
        listOf(Viewer, Forecaster, *qw, Moderator, Owner).map { role ->
            option {
                value = role.id
                +role.name
                if (!canChangeRole(appState, room, role) || (props.isGuest && !role.isAvailableToGuests))
                    disabled = true
            }
        }
    }
}