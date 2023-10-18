package components.redesign.rooms

import components.redesign.HelpIcon
import components.redesign.basic.Stack
import components.redesign.calibration.CalibrationReqView
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
    var who by useState<CalibrationWho>(Myself)

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

    Stack {
        component = ReactHTML.main
        css {
            marginTop = 15.px
            flexDirection = FlexDirection.column
            flexGrow = number(1.0)
            width = layoutMode.contentWidth
            marginLeft = Auto.auto
            marginRight = Auto.auto
        }
        Stack {
            css {
                padding = if (layoutMode >= LayoutMode.TABLET) Padding(15.px, 0.px) else 15.px
                paddingTop = 0.px
                background = bgColor
                borderRadius = 5.px
                flexShrink = number(0.0)
            }
            direction = FlexDirection.row
            QuestionEstimateTabButton {
                +"My calibration"
                active = (who == Myself)
                onClick = { who = Myself }
            }
            QuestionEstimateTabButton {
                +"Group calibration"
                active = ( who ==  Everyone )
                onClick = { who = Everyone }
            }
        }
        CalibrationReqView {
            req = CalibrationRequest(rooms = setOf(props.room.ref), who = who)
            graphHeight = 260.0
        }
    }
}