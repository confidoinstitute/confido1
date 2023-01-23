package components.profile

import csstype.*
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

enum class CheckStatus {
    CHECKING,
    FAILURE,
    SUCCESS,
}

external interface VerifyTokenProps : Props {
    var url: String
    var successTitle: String
    var successText: String
    var failureTitle: String
    var failureText: String
}

val VerifyToken = FC<VerifyTokenProps> { props ->
    val searchParams by useSearchParams()
    val navigate = useNavigate()
    val verificationToken = searchParams.get("t") ?: ""

    var status by useState(CheckStatus.CHECKING)

    useEffectOnce {
        CoroutineScope(EmptyCoroutineContext).launch {
            val response = Client.httpClient.postJson(props.url, TokenVerification(verificationToken)) {}
            status = if (response.status == HttpStatusCode.OK) {
                CheckStatus.SUCCESS
            } else {
                CheckStatus.FAILURE
            }
        }
    }

    Backdrop {
        this.open = status == CheckStatus.CHECKING
        this.sx { this.zIndex = integer(42) }
        CircularProgress {}
    }

    Paper {
        sx {
            marginTop = themed(2)
            padding = themed(2)
        }

        if (status == CheckStatus.FAILURE) {
            Alert {
                severity = AlertColor.error
                AlertTitle {
                    +props.failureTitle
                }
                +props.failureText
            }
        } else if (status == CheckStatus.SUCCESS) {
            Alert {
                severity = AlertColor.success
                AlertTitle {
                    +props.successTitle
                }
                +props.successText
            }
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