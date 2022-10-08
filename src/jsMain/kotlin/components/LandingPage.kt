package components

import mui.material.*
import payloads.requests.Login
import react.*
import users.DebugAdmin

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
    // TODO: Landing page.
}
