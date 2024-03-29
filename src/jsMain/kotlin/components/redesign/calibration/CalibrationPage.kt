package components.redesign.calibration

import components.redesign.HelpIcon
import components.redesign.basic.LayoutWidthWrapper
import components.redesign.basic.MobileSidePad
import components.redesign.basic.PageHeader
import components.redesign.forms.IconButton
import csstype.Color
import csstype.px
import csstype.rem
import emotion.react.css
import payloads.requests.CalibrationRequest
import payloads.requests.Myself
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.useRef


val CalibrationPage = FC<Props> {
    val calibrationHelpOpen = useRef<(CalibrationHelpSection)->Unit>()
    PageHeader {
        title = "Calibration"
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