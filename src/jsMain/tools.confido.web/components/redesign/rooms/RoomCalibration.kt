package tools.confido.web.components.redesign.rooms

import tools.confido.web.components.redesign.HelpIcon
import tools.confido.web.components.redesign.basic.LayoutWidthWrapper
import tools.confido.web.components.redesign.basic.MobileSidePad
import tools.confido.web.components.redesign.basic.Stack
import tools.confido.web.components.redesign.calibration.CalibrationHelpSection
import tools.confido.web.components.redesign.calibration.CalibrationReqView
import tools.confido.web.components.redesign.calibration.TabbedCalibrationReqView
import tools.confido.web.components.redesign.forms.IconButton
import tools.confido.web.components.redesign.layout.LayoutMode
import tools.confido.web.components.redesign.layout.LayoutModeContext
import tools.confido.web.components.redesign.questions.QuestionEstimateTabButton
import web.cssom.*
import emotion.react.css
import tools.confido.payloads.requests.CalibrationRequest
import tools.confido.payloads.requests.CalibrationWho
import tools.confido.payloads.requests.Everyone
import tools.confido.payloads.requests.Myself
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.router.dom.Link
import tools.confido.rooms.Room
import tools.confido.refs.ref

external interface RoomCalibrationProps: Props {
    var room: Room
}

private val bgColor = Color("#f2f2f2")
val RoomCalibration = FC<RoomCalibrationProps> { props->
    val layoutMode = use(LayoutModeContext)!!
    val calibrationHelpOpen = useRef<(CalibrationHelpSection)->Unit>()

    RoomHeader {
        Stack {
            direction = FlexDirection.row
            css {
                flexGrow = number(1.0)
                alignItems = AlignItems.center
                justifyItems = JustifyItems.center
                marginLeft = 5.px
                marginRight = 5.px
                gap = 0.px
                fontSize = 14.px
                fontWeight = integer(500)
                color = Color("#888")
            }
            div { css {flexGrow = number(1.0)} }
            ReactHTML.div { +"Calibration is a measure of how well one's confidence tends to match their accuracy." }
            div {
                css { flexGrow = number(1.0) }
                IconButton {
                    onClick = { calibrationHelpOpen.current?.invoke(CalibrationHelpSection.INTRO) }
                    css {
                        flexShrink = number(0.0)
                    }
                    HelpIcon {}
                }
            }
        }
    }

    LayoutWidthWrapper {
        css { paddingBottom = 30.px }
        MobileSidePad  {
            css { marginBottom = 15.px; fontSize = 0.9.rem; color = Color("#444") }
            +"Showing calibration for questions in room "
            i { +props.room.name }
            +". "
            Link { css { color = Globals.inherit }; to = "/calibration"; +"Show calibration across the whole workspace." }
        }
        TabbedCalibrationReqView {
            req = CalibrationRequest(rooms = setOf(props.room.ref), who = Myself)
            graphHeight = 260.0
            externalHelpOpen = calibrationHelpOpen
        }
    }
}