package components.nouser

import csstype.*
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mui.material.*
import mui.system.sx
import payloads.requests.*
import react.*
import react.router.useNavigate
import utils.themed
import kotlin.coroutines.EmptyCoroutineContext

import react.FC
import react.router.dom.useSearchParams
import utils.postJson

val EmailLogin = FC<Props> {
    val searchParams by useSearchParams()
    val navigate = useNavigate()
    val loginToken = searchParams.get("t") ?: ""

    // false = currently checking login, true = login failed
    var failed by useState(false)

    useEffectOnce {
        CoroutineScope(EmptyCoroutineContext).launch {
            val response = Client.httpClient.postJson("/login_email", EmailLogin(loginToken)) {}
            if (response.status == HttpStatusCode.Unauthorized) {
                failed = true
            } else {
                val url: String = response.body()
                // TODO: Maybe close tab instead?
                navigate(url)
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
