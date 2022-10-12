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

val VerifyEmail = FC<Props> {
    val searchParams by useSearchParams()
    val navigate = useNavigate()
    val verificationToken = searchParams.get("t") ?: ""

    var status by useState(CheckStatus.CHECKING)

    useEffectOnce {
        CoroutineScope(EmptyCoroutineContext).launch {
            val response = Client.httpClient.postJson("/profile/email/verify", EmailVerification(verificationToken)) {}
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
                    +"Email verification failed"
                }
                +"The verification link is expired or invalid."
            }
        } else if (status == CheckStatus.SUCCESS) {
            Alert {
                severity = AlertColor.success
                AlertTitle {
                    +"Email verification success"
                }
                +"Your email address has been successfully verified."
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