package components.redesign.rooms

import components.AppStateContext
import components.redesign.basic.RoomPalette
import components.redesign.basic.Stack
import components.redesign.basic.createRipple
import components.redesign.basic.rippleCss
import csstype.*
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.router.dom.Link
import react.router.useParams
import react.useContext
import rooms.Room

external interface RoomListProps : Props {
    var small: Boolean
    var canCreate: Boolean
}

val roomItemCss = emotion.css.ClassName {
    display = Display.flex
    flexDirection = FlexDirection.row
    gap = 12.px
    textDecoration = None.none
    fontFamily = FontFamily.sansSerif
    alignItems = AlignItems.center
    fontSize = 15.px
    lineHeight = 17.px
    color = Color("#222222")
    padding = Padding(10.px, 20.px)
    margin = 0.px
    background = None.none
    border = None.none
    cursor = Cursor.pointer

    hover {
        backgroundColor = rgba(0,0,0, 0.05)
    }

    rippleCss()
}

val RoomList = FC<RoomListProps> {props ->
    val (appState, stale) = useContext(AppStateContext)
    val location = useParams()

    val iconSize = if(props.small) 36.px else 48.px

    Stack {
        direction = FlexDirection.column
        appState.rooms.map {(id, room) ->
            Link {
                key = id
                to = room.urlPrefix
                onClick = {createRipple(it, Color("#000000"))}
                css(roomItemCss) {}
                div {
                    css {
                        backgroundColor = RoomPalette.red.color
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
            Link {
                css(roomItemCss) {}
                key = "::new_room"
                to = "/new_room"
                onClick = {createRipple(it, Color("#000000"))}
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
