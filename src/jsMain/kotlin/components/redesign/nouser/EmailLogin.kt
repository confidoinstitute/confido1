package components.redesign.nouser

import components.LoginContext
import components.redesign.basic.Alert
import components.redesign.basic.Backdrop
import components.redesign.basic.MainPalette
import components.redesign.forms.Button
import csstype.*
import emotion.react.css
import io.ktor.client.call.*
import payloads.requests.*
import react.*
import react.router.useNavigate
import utils.themed

import react.FC
import react.dom.html.ReactHTML.h3
import react.router.dom.useSearchParams
import utils.runCoroutine

val EmailLogin = FC<Props> {
    val loginState = useContext(LoginContext)
    val searchParams by useSearchParams()
    val navigate = useNavigate()
    val loginToken = searchParams.get("t") ?: ""

    // false = currently checking login, true = login failed
    var failed by useState(false)

    useEffectOnce {
        runCoroutine {
            Client.sendData("/login_email", EmailLogin(loginToken), onError = { failed = true }) {
                navigate(body<String>())
                loginState.login()
            }
        }
    }

    if (!failed) {
        Backdrop {
            css { zIndex = integer(42) }
        }
    }

    if (failed) {
        Alert {
            h3 {
                +"Authentication failed"
            }
            +"The login link is expired or invalid."
        }

        Button {
            palette = MainPalette.default
            onClick = {
                navigate("/")
            }
            +"Go back"
        }
    }
}
