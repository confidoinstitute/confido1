package components.presenter

import components.redesign.basic.Stack
import components.redesign.calibration.CalibrationGraph
import components.redesign.calibration.CalibrationReqView
import csstype.*
import emotion.react.css
import hooks.useSuspendResult
import io.ktor.client.call.*
import io.ktor.http.*
import react.FC
import react.dom.html.ReactHTML.div
import tools.confido.calibration.CalibrationVector
import tools.confido.state.CalibrationReqPV

val CalibrationReqPP = FC<PresenterPageProps<CalibrationReqPV>> { props->
    val req = props.view.req
    val data = useSuspendResult(req.identify()) {
        val resp = Client.sendDataRequest("/calibration", req)
        if (resp.status.isSuccess()) resp.body<CalibrationVector>()
        else null
    }
    val entries = data?.entries?.filter { it.value.total > 0 } ?: emptyList()
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
                    this.calib = data
                    this.height = 40.vh
                }
            }
        }
    }
}