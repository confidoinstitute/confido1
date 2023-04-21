package components.redesign.rooms

import components.*
import components.redesign.basic.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.svg.*
import react.dom.svg.ReactSVG.svg
import react.router.*
import react.router.dom.*
import utils.*

external interface RoomListProps : Props {
    var small: Boolean
    var canCreate: Boolean
}

external interface RoomLinkProps : LinkProps {
    var small: Boolean
    var active: Boolean
}

private val RoomLink = Link.withRipple().withStyleLM<RoomLinkProps>("small") {props, layoutMode ->
    display = Display.flex
    flexDirection = FlexDirection.row
    gap = 12.px
    textDecoration = None.none
    fontFamily = sansSerif
    alignItems = AlignItems.center
    if (props.small) {
        fontSize = 15.px
        lineHeight = 17.px
    } else {
        fontSize = 18.px
        lineHeight = 20.px
    }
    color = Color("#222222")
    padding = Padding(10.px, if (layoutMode == LayoutMode.PHONE) 20.px else 10.px)
    margin = if (layoutMode == LayoutMode.PHONE)  0.px else Margin(0.px, (-10).px)
    background = None.none
    border = None.none
    // On mobile it goes to the edge od the screen so no rounding
    borderRadius = if (layoutMode == LayoutMode.PHONE) 0.px else 5.px
    cursor = Cursor.pointer

    if (props.active) {
        backgroundColor = Color("#F2F2F2")
        borderRadius = 12.px
    }

    hover {
        backgroundColor = rgba(0,0,0, 0.05)
    }
}

val RoomList = FC<RoomListProps> {props ->
    val (appState, stale) = useContext(AppStateContext)
    val location = useLocation()
    val layoutMode = useContext(LayoutModeContext)

    val iconSize = if(props.small) 36.px else 48.px

    Stack {
        direction = FlexDirection.column
        appState.rooms.map {(id, room) ->
            RoomLink {
                key = id
                to = room.urlPrefix
                small = props.small
                div {
                    css {
                        backgroundColor = room.color.palette.color
                        borderRadius = 8.px
                        width = iconSize
                        height = iconSize
                        flexShrink = number(0.0)
                    }
                }
                span {
                    +room.name
                }
                active = location.pathname.startsWith(room.urlPrefix)
            }
        }
        if (props.canCreate)
            RoomLink {
                key = "::new_room"
                to = "/new_room"
                small = props.small
                div {
                    css {
                        border = Border(1.px, LineStyle.solid, Color("#BBBBBB"))
                        borderRadius = 8.px
                        width = iconSize
                        height = iconSize
                        display = Display.flex
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.center
                        flexShrink = number(0.0)
                    }
                    svg {
                        width = 12.0
                        height = 12.0
                        strokeWidth = 2.0
                        stroke = "#BBBBBB"
                        ReactSVG.path {
                            d = "M6,0 L6,12 M0,6 L12,6"
                        }
                    }
                }
                span {
                    +"Create a new room…"
                }
            }
    }
}
