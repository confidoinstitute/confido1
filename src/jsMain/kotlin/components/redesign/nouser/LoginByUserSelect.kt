package components.redesign.nouser

import Client
import components.*
import components.redesign.basic.*
import csstype.*
import dom.html.*
import emotion.react.*
import hooks.*
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.js.*
import mui.material.*
import mui.material.Container
import mui.system.sx
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.input
import tools.confido.refs.*
import users.*
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
            Client.send("/login_users", HttpMethod.Get, onError = {showError(it)}) {
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
            Client.sendData("/login_users", it.ref, onError = { showError(it) }) {
                loginState.login()
            }
        }
    }
    autocomplete {
        options = users ?: emptyArray()
        renderInput = { params ->
            TextField.create {
                sx {
                    input {
                        color = MainPalette.login.text.color
                        // TODO: there is fontSize 14 and weight 400 in the design, but the placeholder doesn't fit
                        fontSize = 13.px
                        fontWeight = integer(300)
                        lineHeight = 17.px
                    }
                    // TODO(Prin): Style border!
                }
                Object.assign(this, params)
                margin = FormControlMargin.normal
                //placeholder = "User name or e-mail"
                placeholder = "Choose account to see Confido from their view"
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

    components.redesign.forms.Button {
        css {
            marginTop = 14.px
            width = 100.pct
            borderRadius = 10.px
        }
        type = ButtonType.submit
        this.palette = MainPalette.default
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
