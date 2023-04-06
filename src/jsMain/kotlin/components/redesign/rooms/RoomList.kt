package components.redesign.rooms

import components.*
import components.redesign.basic.*
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
}

private val RoomLink = Link.withRipple().withStyle<RoomLinkProps>("small") {props ->
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
    padding = Padding(10.px, 20.px)
    margin = 0.px
    background = None.none
    border = None.none
    cursor = Cursor.pointer

    hover {
        backgroundColor = rgba(0,0,0, 0.05)
    }
}

val RoomList = FC<RoomListProps> {props ->
    val (appState, stale) = useContext(AppStateContext)
    val location = useParams()

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
                        backgroundColor = roomPalette(room.id).color
                        borderRadius = 8.px
                        width = iconSize
                        height = iconSize
                        flexShrink = number(0.0)
                    }
                }
                span {
                    +room.name
                }
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
