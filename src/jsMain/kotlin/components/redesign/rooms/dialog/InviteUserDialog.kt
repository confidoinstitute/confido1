package components.redesign.rooms.dialog

import components.redesign.basic.*
import components.redesign.forms.*
import components.redesign.rooms.RoomContext
import web.cssom.*
import emotion.react.css
import hooks.useSubscription
import payloads.requests.AddedExistingMember
import payloads.requests.AddedNewMember
import payloads.responses.WSData
import react.*
import tools.confido.refs.ref
import tools.confido.state.Topics
import react.dom.html.ReactHTML
import rooms.RoomRole
import tools.confido.refs.ref
import tools.confido.state.appConfig
import tools.confido.utils.stringToColor
import users.User
import utils.runCoroutine

internal external interface AddMemberItemProps : Props {
    var user: User?
    var email: String?
    var role: RoomRole
}

internal val AddMemberItem = FC<AddMemberItemProps> {props ->
    val room = use(RoomContext)!!
    fun add() {
        val member = props.user?.let {
            AddedExistingMember(it.ref, props.role)
        } ?: props.email?.let {
            AddedNewMember(it, props.role)
        }
        console.log(member)
        member?.let { addedMember -> runCoroutine {
            console.log("Adding user")
            Client.sendData("${room.urlPrefix}/members/add", addedMember, onError = {showError(it)}) {}
        } }
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
                backgroundColor = Color(stringToColor(props.user?.id ?: props.email ?: ""))
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
                props.user?.let {
                    +it.displayName
                } ?: +(props.email ?: "")
            }
            ReactHTML.div {
                css {
                    fontFamily = sansSerif
                    fontSize = 12.px
                    lineHeight = 15.px
                    color = rgb(0, 0, 0, 0.5)
                }
                props.user?.let {
                    +(it.email ?: "")
                } ?: +"New member"
            }
        }
        Button {
            css {
                padding = Padding(5.px, 8.px)
                margin = 0.px
                fontWeight = integer(500)
                fontSize = 15.px
                lineHeight = 18.px
            }
            props.user?.let {
                +"Add"
            } ?: +"Invite"
            onClick = {add()}
        }
    }
}

val AddMemberDialog = dialogStateWrapper(FC<BaseDialogProps> { props ->
    val room = use(RoomContext)!!

    // Default to the first guest-available role, or the first role
    val defaultRole = appConfig.roles.firstOrNull { it.availableToGuests } ?: appConfig.roles.first()
    var role by useState<RoomRole>(defaultRole)
    var search by useState("")

    // Existing direct members, to exclude from the add list. This dialog is reachable
    // only with MANAGE_MEMBERS, so the members payload carries userIds for us.
    val membersWS = useSubscription(Topics.roomMembers(room.ref))
    val members = ((membersWS as? WSData)?.data ?: emptyList())
        .filter { !it.implied && it.invitedVia == null }
        .mapNotNull { it.user.userId?.id }.toSet()

    // The addable-user directory comes from the users topic (subscribed here, not
    // globally — this dialog is a MANAGE_MEMBERS surface).
    val usersWS = useSubscription(Topics.users())
    val allUsers = (usersWS as? WSData)?.data ?: emptyMap()

    val users = useMemo(allUsers) {
        allUsers.values.sortedWith(
            compareBy({ it.type }, { (it.nick ?: it.email ?: "") })
        ).mapNotNull {
            if (!it.isAnonymous() && it.id !in members) it else null
        }
    }
    val filteredUsers = useMemo(search, users) {
        if (search.isEmpty())
            emptyList()
        else users.filter {user ->
            val inNick = user.nick?.contains(search, true) ?: false
            val inEmail = user.email?.contains(search, true) ?: false
            inNick || inEmail
        }
    }
    val newEmail = search.contains("@") && !allUsers.values.any {
        it.email == search
    }


    Dialog {
        open = props.open
        onClose = props.onClose
        fullSize = true
        title = "Add members"
        disabledAction = false

        FormSection {
            FormField {
                title = "Added member role"
                Select {
                    css {
                        width = important(100.pct)
                    }
                    value = role.id
                    onChange = { event ->
                        role = appConfig.roleById(event.target.value)
                            ?: error("Unknown role: ${event.target.value}")
                    }
                    // Show guest-available roles for adding members
                    appConfig.roles.filter { it.availableToGuests }.map { r ->
                        ReactHTML.option {
                            this.value = r.id
                            +r.name
                        }
                    }
                }
                comment = "The added member will get this role."
            }
            FormField {
                title = "Member's name or e-mail"
                TextInput {
                    value = search
                    onChange = { search = it.target.value }
                }
                comment = "Choose from users with a given name or e-mail. If no such user exists, add this user."
            }
        }
        FormSection {
            css {
                flexGrow = number(1.0)
            }
            title = "Add member"
            filteredUsers.map {
                AddMemberItem {
                    user = it
                    this.role = role
                }
            }
        }
        if (newEmail) {
            FormSection {
                title = "Invite as guest"
                AddMemberItem {
                    email = search
                    this.role = role
                }
            }
        }
    }
})
