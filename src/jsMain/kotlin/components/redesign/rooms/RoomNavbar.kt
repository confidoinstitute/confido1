package components.redesign.rooms

import components.redesign.basic.RoomPalette
import components.redesign.forms.Button
import components.rooms.RoomContext
import csstype.*
import emotion.react.css
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML
import react.useContext

external interface RoomNavbarProps : PropsWithChildren {
    var onNavigateBack: (() -> Unit)?
    var onFeedback: (() -> Unit)?
    var onMenu: (() -> Unit)?
}

val RoomNavbar = FC<RoomNavbarProps> { props ->
    val room = useContext(RoomContext)
    val palette = RoomPalette.red

    ReactHTML.div {
        css {
            backgroundColor = palette.color
            flexBasis = 44.px
            flexShrink = number(0.0)
            display = Display.flex
            flexDirection = FlexDirection.row
            height = 44.px
            top = 0.px
            width = 100.pct
        }
        ReactHTML.div {
            css {
                flexGrow = number(1.0)
                display = Display.flex
                justifyContent = JustifyContent.flexStart
            }
            Button {
                onClick = { props.onNavigateBack?.invoke() }
                css {
                    borderRadius = 100.pct
                }
            }
        }
        +props.children
        ReactHTML.div {
            css {
                flexGrow = number(1.0)
                display = Display.flex
                justifyContent = JustifyContent.flexEnd
            }
            Button {
                onClick = { props.onFeedback?.invoke() }
                css {
                    borderRadius = 100.pct
                }
            }

            Button {
                onClick = { props.onMenu?.invoke() }
                css {
                    borderRadius = 100.pct
                }
            }
        }
    }
}

