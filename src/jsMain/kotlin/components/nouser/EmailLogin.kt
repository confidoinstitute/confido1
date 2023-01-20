package components.nouser

import csstype.*
import io.ktor.client.call.*
import mui.material.*
import mui.system.sx
import payloads.requests.*
import react.*
import react.router.useNavigate
import utils.themed

import react.FC
import react.router.dom.useSearchParams
import utils.runCoroutine

val EmailLogin = FC<Props> {
    val searchParams by useSearchParams()
    val navigate = useNavigate()
    val loginToken = searchParams.get("t") ?: ""

    // false = currently checking login, true = login failed
    var failed by useState(false)

    useEffectOnce {
        runCoroutine {
            Client.sendData("/login_email", EmailLogin(loginToken), onError = { failed = true }) {
                navigate(body<String>())
            }
        }
    }

    Backdrop {
        this.open = !failed
        this.sx { this.zIndex = integer(42) }
        CircularProgress {}
    }

    if (failed) {
        Paper {
            sx {
                marginTop = themed(2)
                padding = themed(2)
            }
            Alert {
                severity = AlertColor.error
                AlertTitle {
                    +"Authentication failed"
                }
                +"The login link is expired or invalid."
            }

            Box {
                sx {
                    textAlign = TextAlign.center
                    marginTop = themed(2)
                }
                Button {
                    variant = ButtonVariant.contained
                    onClick = {
                        navigate("/")
                    }
                    +"Go back"
                }
            }
        }
    }
}

val EmailLoginAlreadyLoggedIn = FC<Props> {
    val navigate = useNavigate()

    useEffect {
        navigate("/")
    }
}
