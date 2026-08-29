package tools.confido.web.components.redesign.nouser

import tools.confido.api.Api
import tools.confido.web.Client
import tools.confido.web.components.*
import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.forms.Select
import web.cssom.*
import web.html.*
import emotion.css.css
import emotion.react.*
import tools.confido.web.hooks.*
import io.ktor.client.call.*
import io.ktor.client.request.get
import io.ktor.http.*
import tools.confido.payloads.requests.UsernameLogin
import react.*
import react.dom.html.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.option
import tools.confido.refs.*
import tools.confido.state.UserSessionValidity
import tools.confido.users.*
import tools.confido.web.utils.*


internal fun groupBy(u: User) = if (u.type.isProper()) "Organization users" else "Guests"

internal fun getOptionLabel(option: User) = option.nick ?: option.email ?: "Temporary guest"

external interface LoginByUserSelectFormProps : Props {
    var helperText: String?
    var demoMode: Boolean
}

val LoginByUserSelectInner = FC<LoginByUserSelectFormProps> { props->
    val loginState = use(LoginContext)!!
    var chosenUser by useState<User?>(null)

    val login = useCoroutineLock()

    val users = useSuspendResult {
        Client.call(Api.loginUsersList()).sortedBy { it.type }
    }

    fun attemptLogin() = login {
        chosenUser?.let {
            Client.call(Api.loginUsers(), UsernameLogin(it.ref, validity = UserSessionValidity.PERMANENT))
            loginState.login()
        }
    }
    Select {
        onChange = { ev->
            println("selected: ${ev.target.value}")
            chosenUser = users?.first { it.id == ev.target.value }
            }
        option {
            disabled=true
            selected=true
            value = ""
            +"Please select"
        }
        users?.forEach { user->
            option {
                value = user.id
                +(user.nick ?: user.email ?: "anonymous user")
            }
        }
    }

    tools.confido.web.components.redesign.forms.Button {
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
    div {
        css {
            maxWidth = 500.px
            padding = 10.px
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
        }

        LoginByUserSelectInner {+props}
    }
}
