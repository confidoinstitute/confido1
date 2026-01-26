package extensions.calibration_training

import components.AppStateContext
import components.redesign.basic.*
import components.redesign.rooms.RoomLink
import emotion.react.css
import js.objects.unsafeJso as jso
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.router.RouteObject
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.SidebarPlace
import web.cssom.*
import web.location.location

/**
 * Training/fitness dumbbell icon for calibration training.
 */
external interface TrainingIconProps : PropsWithClassName {
    var size: Int?
    var color: String?
}

val TrainingIcon = FC<TrainingIconProps> { props ->
    val size = props.size ?: 24
    val color = props.color ?: "currentColor"
    
    svg {
        className = props.className
        width = size.toDouble()
        height = size.toDouble()
        viewBox = "0 0 24 24"
        fill = "none"
        
        // Dumbbell icon - horizontal bar with weights on each end
        // Left weight (outer)
        path {
            d = "M2 8.5C2 7.67 2.67 7 3.5 7C4.33 7 5 7.67 5 8.5V15.5C5 16.33 4.33 17 3.5 17C2.67 17 2 16.33 2 15.5V8.5Z"
            fill = color
        }
        // Left weight (inner)
        path {
            d = "M5 9.5C5 8.67 5.67 8 6.5 8C7.33 8 8 8.67 8 9.5V14.5C8 15.33 7.33 16 6.5 16C5.67 16 5 15.33 5 14.5V9.5Z"
            fill = color
        }
        // Center bar
        path {
            d = "M8 11.5H16V12.5H8V11.5Z"
            fill = color
        }
        // Right weight (inner)
        path {
            d = "M16 9.5C16 8.67 16.67 8 17.5 8C18.33 8 19 8.67 19 9.5V14.5C19 15.33 18.33 16 17.5 16C16.67 16 16 15.33 16 14.5V9.5Z"
            fill = color
        }
        // Right weight (outer)
        path {
            d = "M19 8.5C19 7.67 19.67 7 20.5 7C21.33 7 22 7.67 22 8.5V15.5C22 16.33 21.33 17 20.5 17C19.67 17 19 16.33 19 15.5V8.5Z"
            fill = color
        }
    }
}

/**
 * Client extension for calibration training.
 */
object CalibrationTrainingCE : ClientExtension, CalibrationTrainingExtension() {
    
    override fun sidebarExtra(place: SidebarPlace, small: Boolean, cb: ChildrenBuilder) {
        if (place != SidebarPlace.WORKSPACE_BOTTOM) return
        
        val iconSize = if (small) 36.px else 48.px
        val iconSizeInner = if (small) 24 else 32
        
        cb.apply {
            RoomLink {
                key = "::training"
                to = "/training"
                this.small = small
                highlighted = location.pathname == "/training" || location.pathname.startsWith("/training/")
                
                div {
                    css {
                        width = iconSize
                        height = iconSize
                        display = Display.flex
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.center
                        flexShrink = number(0.0)
                    }
                    TrainingIcon {
                        size = iconSizeInner
                        color = "#bbb"
                    }
                }
                span {
                    +"Calibration training"
                }
            }
        }
    }
    
    override fun rootRoutesExtra(): List<RouteObject> {
        return listOf(
            jso {
                path = "training"
                element = TrainingPage.create()
            }
        )
    }
}
