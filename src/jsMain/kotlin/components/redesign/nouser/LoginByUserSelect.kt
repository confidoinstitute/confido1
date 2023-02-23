package components.redesign.nouser

import components.LoginContext
import components.UserAvatar
import components.showError
import components.userListItemText
import csstype.*
import dom.html.HTMLLIElement
import emotion.react.css
import hooks.useCoroutineLock
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.js.Object
import kotlinx.js.ReadonlyArray
import kotlinx.js.jso
import mui.material.*
import mui.system.sx
import react.*
import react.dom.html.HTMLAttributes
import tools.confido.refs.ref
import users.User
import utils.*


internal fun groupBy(u: User) = if (u.type.isProper()) "Organization users" else "Guests"

internal fun getOptionLabel(option: User) = option.nick ?: option.email ?: "Temporary guest"

internal fun renderOption(
    attributes: HTMLAttributes<HTMLLIElement>,
    option: User,
    state: AutocompleteRenderOptionState
) =
    ListItem.create {
        Object.assign(this, attributes)
        ListItemAvatar {
            UserAvatar {
                user = option
            }
        }
        +userListItemText(option, withInactive = true)
    }

external interface LoginByUserSelectFormProps : Props {
    var helperText: String?
    var demoMode: Boolean
}

val LoginByUserSelectInner = FC<LoginByUserSelectFormProps> { props->
    val loginState = useContext(LoginContext)
    var chosenUser by useState<User?>(null)
    var users by useState<ReadonlyArray<User>?>(null)
    var open by useState(false)
    val loading = open && users == null

    val login = useCoroutineLock()

    useEffect(loading) {
        if (!loading) {
            return@useEffect
        }

        runCoroutine {
            Client.send("/login_users", HttpMethod.Get, onError = {showError?.invoke(it)}) {
                val availableUsers: ReadonlyArray<User> = body()
                // Required for the autocomplete groupBy
                availableUsers.sortBy { it.type }
                users = availableUsers
            }
        }
    }

    useEffect(open) {
        if (!open) {
            users = null
        }
    }
    val autocomplete: FC<AutocompleteProps<User>> = Autocomplete
    fun attemptLogin() = login {
        chosenUser?.let {
            Client.sendData("/login_users", it.ref, onError = { showError?.invoke(it) }) {
                loginState.login()
            }
        }
    }
    autocomplete {
        options = users ?: emptyArray()
        renderInput = { params ->
            TextField.create {
                Object.assign(this, params)
                margin = FormControlMargin.normal
                placeholder = "User name or e-mail"
                label = ReactNode("Choose account to see Confido from their view")
                helperText = props.helperText?.let { ReactNode(it) }
            }
        }
        getOptionDisabled = { option -> !option.active }
        renderOption = ::renderOption
        autoComplete = true
        getOptionLabel = ::getOptionLabel
        groupBy = ::groupBy
        ListboxComponent = List
        ListboxProps = jso<ListProps> {
            dense = true
        }
        onChange = { _, value: User, _, _ -> chosenUser = value }
        fullWidth = true
        this.loading = loading
        this.open = open
        onOpen = { open = true }
        onClose = { _, _ -> open = false }
    }

    Button {
        variant = ButtonVariant.contained
        fullWidth = true
        disabled = chosenUser == null || login.running
        onClick = { attemptLogin() }
        +"Log in"
    }
}

val LoginByUserSelectForm = FC<LoginByUserSelectFormProps> { props ->
    Container {
        maxWidth = byTheme("xs")
        sx {
            padding = themed(2)
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
        }

        LoginByUserSelectInner {+props}
    }
}
