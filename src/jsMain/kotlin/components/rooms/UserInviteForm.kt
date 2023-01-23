package components.rooms

import components.AppStateContext
import components.UserAvatar
import components.userListItemText
import csstype.AlignItems
import csstype.number
import csstype.rem
import kotlinx.js.Object
import kotlinx.js.ReadonlyArray
import mui.base.FilterOptionsState
import mui.material.*
import mui.system.responsive
import mui.system.sx
import dom.html.HTMLLIElement
import kotlinx.js.jso
import payloads.requests.AddedExistingMember
import payloads.requests.AddedNewMember
import react.*
import react.dom.html.HTMLAttributes
import rooms.*
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
    option == value

internal fun getOptionLabel(option: UserAutocomplete) = when (option) {
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
    val defaultRole = Forecaster

    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    var chosenUsers by useState<Array<UserAutocomplete>>(emptyArray())
    var role by useState<RoomRole>(defaultRole)
    var hasHighlight by useState(false)
    var inputText by useState("")

    val members = room.members.filter {it.invitedVia == null}.map {it.user.id}.toSet()
    val users = useMemo(appState.users, chosenUsers) {
        val options = appState.users.values.sortedWith(
            compareBy({ it.type }, { (it.nick ?: it.email ?: "") })
        ).mapNotNull {
            if (!it.isAnonymous() && it.id !in members) ExistingUser(it.ref) else null
        } + chosenUsers.filterIsInstance<NewUser>().sortedBy { it.email }

        options.toTypedArray()
    }

    val filterOptions: (ReadonlyArray<UserAutocomplete>, FilterOptionsState<UserAutocomplete>) -> ReadonlyArray<UserAutocomplete> = useMemo(users, chosenUsers) {{
        optUsers, state ->
        val value = state.inputValue
        val valueInOptions = users.any {
            when (it) {
                is ExistingUser ->
                    it.userRef.deref()?.email?.equals(value, true) ?: false
                is NewUser -> it.email.equals(value, true)
            }
        }

        val filtered = optUsers.filter {
            when (it) {
                is ExistingUser -> {
                    val user = it.userRef.deref()
                    val inNick = user?.nick?.contains(value, true) ?: false
                    val inEmail = user?.email?.contains(value, true) ?: false
                    inNick || inEmail
                }
                is NewUser -> {
                    val contains = it.email.contains(value, true)
                    contains
                }
            }
        }

        when {
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
            sx {
                flexGrow = number(1.0)
            }
            multiple = true
            options = users
            value = chosenUsers
            renderInput = ::renderInput
            renderOption = ::renderOption
            autoComplete = true
            getOptionLabel = ::getOptionLabel
            isOptionEqualToValue = ::isOptionEqualValue
            groupBy = ::groupBy
            ListboxComponent = List
            ListboxProps = jso<ListProps> {
                dense = true
            }
            onChange = { _, value: Array<UserAutocomplete>, _, _ -> chosenUsers = value }
            this.filterSelectedOptions = true
            this.filterOptions = filterOptions
            onInputChange = { _, s, _ -> inputText = s }
            onOpen = { hasHighlight = false }
            onClose = { _, _ -> hasHighlight = false }
            onHighlightChange = { _, opt, _ -> hasHighlight = (opt != null) }
            onKeyDown = { ev ->
                if (ev.key == "Enter" && !hasHighlight && inputText.contains("@")) {
                    chosenUsers += arrayOf(NewUser(inputText))
                    ev.preventDefault()
                }
            }
        }

        val guestChosen = chosenUsers.any {
            when(it) {
                is ExistingUser -> !(it.userRef.deref()?.type?.isProper() ?: false)
                is NewUser -> true
            }
        }

        // We need to fall back to an acceptable role in case a guest is chosen.
        if (guestChosen && !role.isAvailableToGuests) {
            role = defaultRole
        }

        MemberRoleSelect {
            value = role
            disabled = stale
            // If any chosen user is a guest or a new user, we may need to restrict available roles.
            isGuest = guestChosen
            onChange = { role = it }
        }

        Button {
            sx {
                width = 6.rem
            }
            disabled = chosenUsers.isEmpty() || stale
            if (chosenUsers.any { it is NewUser }) {
                endIcon = icons.SendIcon.create()
                +"Invite"
            } else {
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