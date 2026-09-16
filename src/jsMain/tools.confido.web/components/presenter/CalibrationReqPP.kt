package tools.confido.web.components.presenter

import tools.confido.web.Client
import tools.confido.api.Api
import tools.confido.web.components.basic.Stack
import tools.confido.web.components.calibration.CalibrationGraph
import tools.confido.web.components.calibration.CalibrationReqView
import web.cssom.*
import emotion.react.css
import tools.confido.web.hooks.useSuspendResult
import io.ktor.client.call.*
import io.ktor.http.*
import react.FC
import react.dom.html.ReactHTML.div
import tools.confido.calibration.CalibrationVector
import tools.confido.state.CalibrationReqPV

val CalibrationReqPP = FC<PresenterPageProps<CalibrationReqPV>> { props->
    val req = props.view.req
    val data = useSuspendResult(req.identify()) {
        Client.call(Api.calibration(), req)
    }
    val entries = data?.entries?.filter { it.value.enoughData } ?: emptyList()
    data?.let {
        Stack {
            css {
                margin = 10.pct
                alignItems = AlignItems.center
                justifyContent = JustifyContent.center
                justifySelf = JustifySelf.stretch
                flexGrow = number(1.0)
            }
            div {
                css {
                    width = 60.vh
                    transform = scale(2.0)
                }
                CalibrationGraph {
                    this.who = req.who
                    this.calib = data
                    this.height = 40.vh
                }
            }
        }
    }
}