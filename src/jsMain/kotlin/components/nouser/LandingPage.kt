package components.nouser

import components.AppStateContext
import mui.material.*
import payloads.requests.PasswordLogin
import react.*
import users.DebugAdmin
import users.DebugMember

val LandingPage = FC<Props> {
    val (_, stale) = useContext(AppStateContext)

    Typography { +"Welcome to Confido!" }

    LoginForm {}

    Button {
        onClick = {
            Client.postData("/login", PasswordLogin(DebugAdmin.email, DebugAdmin.password))
        }
        disabled = stale
        +"Log in as debug admin"
    }
    Button {
        onClick = {
            Client.postData("/login", PasswordLogin(DebugMember.email, DebugMember.password))
        }
        disabled = stale
        +"Log in as debug member"
    }
    // TODO: Landing page.
}
