package components.rooms

import components.AppStateContext
import components.UserAvatar
import csstype.AlignItems
import csstype.rem
import kotlinx.browser.window
import kotlinx.js.Object
import kotlinx.js.ReadonlyArray
import mui.base.FilterOptionsState
import mui.material.*
import mui.system.responsive
import mui.system.sx
import org.w3c.dom.HTMLLIElement
import payloads.requests.AddMember
import react.*
import react.dom.html.HTMLAttributes
import rooms.Forecaster
import rooms.RoomPermission
import rooms.RoomRole
import tools.confido.refs.ref
import users.User

internal external interface UserAutocomplete
internal data class ExistingUser(val user: User) : UserAutocomplete
internal data class NewUser(val email: String): UserAutocomplete

internal fun renderInput(params: AutocompleteRenderInputParams) =
    TextField.create {
        kotlinx.js.Object.assign(this, params)
        placeholder = "User name or e-mail"
        label = ReactNode("Add a member")
    }

internal fun getOptionLabel(option: UserAutocomplete) =
    when (option) {
        is ExistingUser -> option.user.nick ?: "<unknown user>"
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
                ListItemText {
                    primary = react.ReactNode(option.user.nick ?: "Anonymous")
                    secondary = react.ReactNode(option.user.email ?: "")
                }
            }
            is NewUser -> {
                ListItemAvatar {
                    Avatar {}
                }
                ListItemText {
                    primary = react.ReactNode("New user…")
                    secondary = react.ReactNode(option.email)
                }
            }
        }
    }

internal fun filterOptions(
    users: ReadonlyArray<UserAutocomplete>,
    state: FilterOptionsState<UserAutocomplete>
): ReadonlyArray<UserAutocomplete> {
    val value = state.inputValue

    val filtered = users.filter {
        val user = (it as ExistingUser).user
        val inNick = user.nick?.contains(value, true) ?: false
        val inEmail = user.email?.contains(value, true) ?: false
        inNick || inEmail
    }

    return when {
        (filtered.size == 1 && state.inputValue == (filtered[0] as ExistingUser).user.email) ->
            filtered.toTypedArray()
        (state.inputValue.contains("@")) ->
            (filtered + listOf(NewUser(value))).toTypedArray()
        else -> filtered.toTypedArray()
    }
}

val UserInviteForm = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    var chosenUser by useState<UserAutocomplete?>(null)
    var role by useState<RoomRole>(Forecaster)

    val members = room.members.map {it.user.id}.toSet()
    val users = appState.users.values.mapNotNull {
        if (it.email != null && it.id !in members) ExistingUser(it) else null
    }.toTypedArray()

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
            ListboxComponent = List
            ListboxProps = utils.jsObject {
                dense = true
            }.unsafeCast<ListProps>()
            onChange = { _, value: UserAutocomplete, _, _ -> chosenUser = value }
            filterOptions = ::filterOptions
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
                    is ExistingUser -> {
                        Client.postData("/rooms/${room.id}/members/add", AddMember(who.user.ref, role))
                        chosenUser = null
                        role = Forecaster
                    }
                    is NewUser -> {
                        // TODO handle actual invitation
                        window.alert(chosenUser.toString())
                    }
                }
            }
        }
    }
}