package components.rooms

import components.AppStateContext
import components.UserAvatar
import components.userListItemText
import csstype.AlignItems
import csstype.rem
import kotlinx.js.Object
import kotlinx.js.ReadonlyArray
import mui.base.FilterOptionsState
import mui.material.*
import mui.system.responsive
import mui.system.sx
import dom.html.HTMLLIElement
import payloads.requests.AddedExistingMember
import payloads.requests.AddedNewMember
import react.*
import react.dom.html.HTMLAttributes
import rooms.Forecaster
import rooms.RoomPermission
import rooms.RoomRole
import tools.confido.refs.*
import users.User

internal sealed external interface UserAutocomplete
internal data class ExistingUser(val userRef: tools.confido.refs.Ref<User>) : UserAutocomplete
internal data class NewUser(val email: String): UserAutocomplete

internal fun renderInput(params: AutocompleteRenderInputParams) =
    TextField.create {
        Object.assign(this, params)
        placeholder = "User name or e-mail"
        label = ReactNode("Add a member")
    }

internal fun groupBy(u: UserAutocomplete) = when(u) {
    is ExistingUser -> if (u.userRef.deref()?.type?.isProper() == true) "Organization users" else "Guests"
    is NewUser -> "Invite as guest"
}

internal fun isOptionEqualValue(option: UserAutocomplete, value: UserAutocomplete) =
    if (value.asDynamic() is String) false else option == value

internal fun getOptionLabel(option: UserAutocomplete) =
    when (option) {
        is ExistingUser -> {
            val user = option.userRef.deref()
            user?.nick ?: user?.email ?: ""
        }
        is NewUser -> option.email
    }
internal fun renderOption(attributes: HTMLAttributes<HTMLLIElement>, option: UserAutocomplete, state: AutocompleteRenderOptionState) =
    ListItem.create {
        Object.assign(this, attributes)
        when (option) {
            is ExistingUser -> {
                option.userRef.deref()?.let {
                    ListItemAvatar {
                        UserAvatar {
                            user = it
                        }
                    }
                    +userListItemText(it)
                }
            }
            is NewUser -> {
                ListItemAvatar {
                    Avatar {}
                }
                ListItemText {
                    primary = react.ReactNode(option.email)
                }
            }
        }
    }


val UserInviteForm = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    var chosenUsers by useState<Array<UserAutocomplete>>(emptyArray())
    var role by useState<RoomRole>(Forecaster)

    val members = room.members.filter {it.invitedVia == null}.map {it.user.id}.toSet()
    val emails = useMemo(appState.users) { appState.users.values.mapNotNull {it.email}.toSet() }
    val users = useMemo(appState.users, chosenUsers) {
        val options = appState.users.values.sortedWith(
            compareBy({ it.type }, { (it.nick ?: it.email ?: "") })
        ).mapNotNull {
            if (!it.isAnonymous() && it.id !in members) ExistingUser(it.ref) else null
        }// + chosenUsers.filterIsInstance<NewUser>().sortedBy { it.email }

        options.toTypedArray()
    }
    useEffect(chosenUsers) {
        console.log(chosenUsers)
    }

    val filterOptions: (ReadonlyArray<UserAutocomplete>, FilterOptionsState<UserAutocomplete>) -> ReadonlyArray<UserAutocomplete> = useMemo(users, chosenUsers) {{
        users, state ->
        val value = state.inputValue
        var valueInOptions = false

        val filtered = users.filter {
            when(it) {
                is ExistingUser -> {
                    val user = it.userRef.deref()
                    val inNick = user?.nick?.contains(value, true) ?: false
                    val inEmail = user?.email?.contains(value, true) ?: false
                    valueInOptions = valueInOptions || user?.email?.equals(value, true) ?: false
                    inNick || inEmail
                }
                is NewUser -> {
                    val contains = it.email.contains(value, true)
                    valueInOptions = valueInOptions || it.email.equals(value, true)
                    contains
                }
            }
        }

        when {
            (filtered.size == 1 && value == ((filtered[0] as ExistingUser).userRef.deref()?.email ?: "")) ->
                filtered.toTypedArray()
            (value.contains("@") && !valueInOptions) ->
                (filtered + listOf(NewUser(value))).toTypedArray()
            else -> filtered.toTypedArray()
        }
    }}

    Stack {
        this.direction = responsive(StackDirection.row)
        this.spacing = responsive(1)
        sx {
            alignItems = AlignItems.center
        }

        val autocomplete: FC<AutocompleteProps<UserAutocomplete>> = Autocomplete
        autocomplete {
            multiple = true
            options = users
            value = chosenUsers
            renderInput = ::renderInput
            renderOption = ::renderOption
            autoComplete = true
            getOptionLabel = ::getOptionLabel
            //isOptionEqualToValue = ::isOptionEqualValue
            groupBy = ::groupBy
            ListboxComponent = List
            ListboxProps = utils.jsObject {
                dense = true
            }.unsafeCast<ListProps>()
            onChange = { _, value: Array<UserAutocomplete>, _, _ -> chosenUsers = value }
            this.filterOptions = filterOptions
            fullWidth = true
        }

        MemberRoleSelect {
            value = role
            disabled = stale
            ownerSelectable = appState.hasPermission(room, RoomPermission.ROOM_OWNER)
            onChange = { role = it }
        }

        Button {
            sx {
                width = 7.rem
            }
            disabled = chosenUsers.isEmpty() || stale
            if (chosenUsers.any { it is NewUser }) {
                    endIcon = icons.SendIcon.create()
                    +"Invite"
                }
                else {
                    startIcon = icons.AddIcon.create()
                    +"Add"
                }

            onClick = {
                chosenUsers.forEach {
                    when (val who = it) {
                        is ExistingUser ->
                            AddedExistingMember(who.userRef, role)
                        is NewUser -> {
                            AddedNewMember(who.email, role)
                        }
                        null -> null
                    }?.let { addedMember ->
                        Client.postData("/rooms/${room.id}/members/add", addedMember)
                    }
                }
                chosenUsers = emptyArray()
                role = Forecaster
            }
        }
    }
}