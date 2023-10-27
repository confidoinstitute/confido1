package components.redesign.rooms

import components.redesign.HelpIcon
import components.redesign.basic.LayoutWidthWrapper
import components.redesign.basic.Stack
import components.redesign.calibration.CalibrationReqView
import components.redesign.calibration.TabbedCalibrationReqView
import components.redesign.forms.IconButton
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.QuestionEstimateTabButton
import csstype.*
import emotion.react.css
import payloads.requests.CalibrationRequest
import payloads.requests.CalibrationWho
import payloads.requests.Everyone
import payloads.requests.Myself
import react.FC
import react.Props
import react.dom.html.ReactHTML
import react.useContext
import react.useState
import rooms.Room
import tools.confido.refs.ref

external interface RoomCalibrationProps: Props {
    var room: Room
}

private val bgColor = Color("#f2f2f2")
val RoomCalibration = FC<RoomCalibrationProps> { props->
    val layoutMode = useContext(LayoutModeContext)

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
            ReactHTML.div { +"Calibration is a measure of how well one's confidence tends to match their accuracy." }
            IconButton {
                css {
                    flexShrink = number(0.0)
                }
                HelpIcon{}
            }
        }
    }

    LayoutWidthWrapper {
        css { paddingBottom = 30.px }
        TabbedCalibrationReqView {
            req = CalibrationRequest(rooms = setOf(props.room.ref), who = Myself)
            graphHeight = 260.0
        }
    }
}