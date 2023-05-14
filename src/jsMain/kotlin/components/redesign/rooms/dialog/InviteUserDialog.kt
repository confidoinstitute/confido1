package components.redesign.rooms.dialog

import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.*
import components.rooms.RoomContext
import components.showError
import csstype.*
import emotion.react.css
import payloads.requests.AddedExistingMember
import payloads.requests.AddedNewMember
import react.*
import react.dom.html.ReactHTML
import rooms.Forecaster
import rooms.QuestionWriter
import rooms.RoomRole
import rooms.Viewer
import tools.confido.refs.ref
import tools.confido.state.FeatureFlag
import tools.confido.state.appConfig
import users.User
import utils.runCoroutine
import utils.stringToColor

internal external interface AddMemberItemProps : Props {
    var user: User?
    var email: String?
    var role: RoomRole
}

internal val AddMemberItem = FC<AddMemberItemProps> {props ->
    val room = useContext(RoomContext)
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
                backgroundColor = stringToColor(props.user?.id ?: props.email ?: "")
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
                    +(it.nick ?: "Anonymous user")
                } ?: +(props.email ?: "")
            }
            ReactHTML.div {
                css {
                    fontFamily = sansSerif
                    fontSize = 12.px
                    lineHeight = 15.px
                    color = rgba(0, 0, 0, 0.5)
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

val AddMemberDialog = FC<DialogProps> {props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    var role by useState<RoomRole>(Forecaster)
    var search by useState("")

    val members = room.members.filter {it.invitedVia == null}.map {it.user.id}.toSet()

    val users = useMemo(appState.users) {
        appState.users.values.sortedWith(
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
    val newEmail = search.contains("@") && !appState.users.values.any {
        it.email == search
    }


    Dialog {
        open = props.open
        onClose = props.onClose
        fullSize = true
        title = "Add members"
        disabledAction = (stale)

        FormSection {
            FormField {
                title = "Added member role"
                Select {
                    css {
                        width = important(100.pct)
                    }
                    value = role.id
                    onChange = { event ->
                        role = when (event.target.value) {
                            "viewer" -> Viewer
                            "forecaster" -> Forecaster
                            "question_writer" -> QuestionWriter
                            else -> error("This should not happen!")
                        }
                    }
                    (mapOf("viewer" to "Viewer", "forecaster" to "Forecaster")
                            + if (FeatureFlag.QUESTION_WRITER_ROLE in appConfig.featureFlags)
                        mapOf("question_writer" to "Question Writer")
                    else emptyMap()
                            ).map { (value, label) ->
                            ReactHTML.option {
                                this.value = value
                                +label
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
}
