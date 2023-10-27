package components.redesign.calibration

import components.redesign.basic.LayoutWidthWrapper
import components.redesign.basic.PageHeader
import csstype.px
import emotion.react.css
import payloads.requests.CalibrationRequest
import payloads.requests.Myself
import react.FC
import react.Props


val CalibrationPage = FC<Props> {
    PageHeader {
        title = "Calibration"
        allowSidebar = true
        navigateBack = "/"
    }
    LayoutWidthWrapper {
        css { paddingBottom = 30.px }
        TabbedCalibrationReqView {
            graphHeight = 400.0
            req = CalibrationRequest(who = Myself)
        }
    }
}