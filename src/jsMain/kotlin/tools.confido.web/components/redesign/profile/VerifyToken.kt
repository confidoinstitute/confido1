package tools.confido.web.components.redesign.profile

import tools.confido.web.Client
import tools.confido.web.ApiError
import tools.confido.web.components.redesign.basic.Alert
import tools.confido.web.components.redesign.basic.Backdrop
import tools.confido.web.components.redesign.basic.Stack
import tools.confido.web.components.redesign.basic.sansSerif
import emotion.react.css
import js.array.component1
import js.array.component2
import web.cssom.*
import tools.confido.payloads.requests.TokenVerification
import tools.confido.utils.BoundApi
import react.FC
import react.Props
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.span
import react.router.dom.Link
import react.router.dom.useSearchParams
import react.router.useNavigate
import react.useEffectOnce
import react.useState
import tools.confido.web.utils.runCoroutine

enum class CheckStatus {
    CHECKING,
    FAILURE,
    SUCCESS,
}

external interface VerifyTokenProps : Props {
    var api: BoundApi<TokenVerification, Unit>
    var successTitle: String
    var successText: String
    var failureTitle: String
    var failureText: String
}

val VerifyToken = FC<VerifyTokenProps> { props ->
    val (searchParams,_) = useSearchParams()
    val navigate = useNavigate()
    val verificationToken = searchParams.get("t") ?: ""

    var status by useState(CheckStatus.CHECKING)

    useEffectOnce {
        runCoroutine {
            status = try {
                Client.call(props.api, TokenVerification(verificationToken))
                CheckStatus.SUCCESS
            } catch (e: ApiError) {
                CheckStatus.FAILURE
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