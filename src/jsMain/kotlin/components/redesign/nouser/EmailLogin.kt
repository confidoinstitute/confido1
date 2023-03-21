package components.redesign.nouser

import Client
import components.*
import components.redesign.basic.*
import components.redesign.forms.*
import csstype.*
import emotion.react.*
import io.ktor.client.call.*
import payloads.requests.*
import react.*
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.p
import react.router.*
import react.router.dom.*
import utils.*

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

    Backdrop {
        this.`in` = !failed
    }
    if (failed) {
        Stack {
            css {
                maxWidth = 400.px
                padding = Padding(0.px, 15.px)
            }
            div {
                css {
                    marginBottom = 3.px
                    padding = Padding(10.px, 12.px)
                    textAlign = TextAlign.center
                    color = Color("#FF7070")  // TODO(Prin): use a palette.
                    width = 100.pct

                    fontSize = 14.px
                    lineHeight = 17.px
                    fontWeight = integer(400)
                    fontFamily = sansSerif
                }
                p {
                    b {
                        +"Unable to log in"
                    }
                }
                p {
                    +"The login link is expired or invalid."
                }
            }

            Button {
                palette = MainPalette.default
                css {
                    width = 100.pct
                }
                onClick = {
                    navigate("/")
                }
                +"Go back"
            }
        }
    }
}
