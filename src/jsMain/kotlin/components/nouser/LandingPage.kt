package components.nouser

import components.AppStateContext
import mui.material.*
import payloads.requests.Login
import react.*
import users.DebugAdmin
import users.DebugMember

val LandingPage = FC<Props> {
    val appState = useContext(AppStateContext)

    Typography { +"Welcome to Confido!" }

    LoginForm {}

    Button {
        onClick = {
            Client.postData("/login", Login(DebugAdmin.email, DebugAdmin.password))
        }
        disabled = appState.stale
        +"Log in as debug admin"
    }
    Button {
        onClick = {
            Client.postData("/login", Login(DebugMember.email, DebugMember.password))
        }
        disabled = appState.stale
        +"Log in as debug member"
    }
    // TODO: Landing page.
}
