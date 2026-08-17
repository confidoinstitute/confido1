package tools.confido.web.components.redesign.calibration

import tools.confido.web.components.redesign.HelpIcon
import tools.confido.web.components.redesign.basic.LayoutWidthWrapper
import tools.confido.web.components.redesign.basic.MobileSidePad
import tools.confido.web.components.redesign.basic.PageHeader
import tools.confido.web.components.redesign.forms.IconButton
import web.cssom.Color
import web.cssom.px
import web.cssom.rem
import emotion.react.css
import tools.confido.payloads.requests.CalibrationRequest
import tools.confido.payloads.requests.Myself
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.useRef


val CalibrationPage = FC<Props> {
    val calibrationHelpOpen = useRef<(CalibrationHelpSection)->Unit>()
    PageHeader {
        title = "Calibration stats"
        allowSidebar = true
        navigateBack = "/"
        rightExtra = IconButton.create {
            HelpIcon{}
            onClick = { calibrationHelpOpen.current?.invoke(CalibrationHelpSection.INTRO) }
        }
    }
    LayoutWidthWrapper {
        css { paddingBottom = 30.px }
        MobileSidePad {
            css { marginBottom = 15.px; fontSize = 0.9.rem; color = Color("#444") }
            +"Showing calibration across the whole workspace. You can also see calibration for questions in an idividual room on the "
            i { +"Calibration"}
            +" tab on the room page."
        }
        TabbedCalibrationReqView {
            graphHeight = 400.0
            req = CalibrationRequest(who = Myself)
            externalHelpOpen = calibrationHelpOpen
        }
    }
}