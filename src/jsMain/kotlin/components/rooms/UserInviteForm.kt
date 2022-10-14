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
import org.w3c.dom.HTMLLIElement
import payloads.requests.AddedExistingMember
import payloads.requests.AddedNewMember
import react.*
import react.dom.html.HTMLAttributes
import rooms.Forecaster
import rooms.RoomPermission
import rooms.RoomRole
import tools.confido.refs.ref
import users.User

internal sealed external interface UserAutocomplete
internal data class ExistingUser(val user: User) : UserAutocomplete
internal data class NewUser(val email: String): UserAutocomplete

internal fun renderInput(params: AutocompleteRenderInputParams) =
    TextField.create {
        kotlinx.js.Object.assign(this, params)
        placeholder = "User name or e-mail"
        label = ReactNode("Add a member")
    }

internal fun groupBy(u: UserAutocomplete) = when(u) {
    is ExistingUser -> if (u.user.type.isProper()) "Organization users" else "Guests"
    is NewUser -> "Invite as guest"
}

internal fun getOptionLabel(option: UserAutocomplete) =
    when (option) {
        is ExistingUser -> option.user.nick ?: option.user.email ?: ""
        is NewUser -> option.email
        else -> option.toString()
    }
internal fun renderOption(attributes: HTMLAttributes<HTMLLIElement>, option: UserAutocomplete, state: AutocompleteRenderOptionState) =
    ListItem.create {
        Object.assign(this, attributes)
        when (option) {
            is ExistingUser -> {
                ListItemAvatar {
                    UserAvatar {
                        user = option.user
                    }
                }
                +userListItemText(option.user)
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
    var chosenUser by useState<UserAutocomplete?>(null)
    var role by useState<RoomRole>(Forecaster)

    val members = room.members.filter {it.invitedVia == null}.map {it.user.id}.toSet()
    val emails = useMemo(appState.users) { appState.users.values.mapNotNull {it.email}.toSet() }
    val users = useMemo(appState.users) {
        appState.users.values.sortedWith(
            compareBy({ it.type }, { (it.nick ?: it.email ?: "") })
        ).mapNotNull {
            if (!it.isAnonymous() && it.id !in members) ExistingUser(it) else null
        }.toTypedArray()
    }

    val filterOptions: (ReadonlyArray<UserAutocomplete>, FilterOptionsState<UserAutocomplete>) -> ReadonlyArray<UserAutocomplete> = useMemo(emails) {{
        users, state ->
        val value = state.inputValue

        val filtered = users.filter {
            val user = (it as ExistingUser).user
            val inNick = user.nick?.contains(value, true) ?: false
            val inEmail = user.email?.contains(value, true) ?: false
            inNick || inEmail
        }

        when {
            (filtered.size == 1 && value == (filtered[0] as ExistingUser).user.email) ->
                filtered.toTypedArray()
            (value.contains("@") && !emails.contains(value)) ->
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
            options = users
            renderInput = ::renderInput
            renderOption = ::renderOption
            autoComplete = true
            getOptionLabel = ::getOptionLabel
            groupBy = ::groupBy
            ListboxComponent = List
            ListboxProps = utils.jsObject {
                dense = true
            }.unsafeCast<ListProps>()
            onChange = { _, value: UserAutocomplete, _, _ -> chosenUser = value }
            this.filterOptions = filterOptions
            freeSolo = true
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
            disabled = chosenUser == null || stale
            when(chosenUser) {
                is NewUser -> {
                    endIcon = icons.SendIcon.create()
                    +"Invite"
                }
                else -> {
                    startIcon = icons.AddIcon.create()
                    +"Add"
                }
            }

            onClick = {
                when(val who = chosenUser) {
                    is ExistingUser ->
                        AddedExistingMember(who.user.ref, role)
                    is NewUser -> {
                        AddedNewMember(who.email, role)
                    }
                    null -> null
                }?.let {addedMember ->
                    Client.postData("/rooms/${room.id}/members/add", addedMember)
                    chosenUser = null
                    role = Forecaster
                }
            }
        }
    }
}