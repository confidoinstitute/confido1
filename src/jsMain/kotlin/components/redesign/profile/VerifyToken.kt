package components.redesign.profile

import components.redesign.basic.Alert
import components.redesign.basic.Backdrop
import components.redesign.basic.Stack
import components.redesign.basic.sansSerif
import emotion.react.css
import csstype.*
import payloads.requests.TokenVerification
import react.FC
import react.Props
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.span
import react.router.dom.Link
import react.router.dom.useSearchParams
import react.router.useNavigate
import react.useEffectOnce
import react.useState
import utils.runCoroutine

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
        runCoroutine {
            Client.sendData(
                props.url,
                TokenVerification(verificationToken),
                onError = { status = CheckStatus.FAILURE }) {
                status = CheckStatus.SUCCESS
            }
        }
    }

    Backdrop {
        this.`in` = status == CheckStatus.CHECKING
    }

    Stack {
        css {
            if (status == CheckStatus.FAILURE)
                color = Color("#FF0000")
            textAlign = TextAlign.center
            fontSize = 16.px
            fontFamily = sansSerif
            gap = 12.px
        }
        if (status == CheckStatus.SUCCESS) {
            b {
                +props.successTitle
            }
            span {
                +props.successText
            }
        } else if (status == CheckStatus.FAILURE) {
            b {
                +props.failureText
            }
            span {
                +props.failureTitle
            }
        }
        Link {
            to = "/"
            +"Go back"
        }
    }
}