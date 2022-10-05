package components.rooms

import components.AppStateContext
import csstype.Color
import csstype.None.none
import emotion.react.css
import mui.material.*
import react.*
import react.router.dom.NavLink

external interface RoomListProps : Props {
    var onNavigate: (() -> Unit)?
}

val RoomList = FC<RoomListProps> {props ->
    val clientAppState = useContext(AppStateContext)
    val state = clientAppState.state

    ListSubheader {
        +"Rooms"
    }
    for (room in state.rooms) {
        ListItem {
            disablePadding = true
            this.key = room.id

            ListItemText {
                NavLink {
                    onClick = { props.onNavigate?.invoke() }
                    to = "/room/${room.id}"
                    css {
                        textDecoration = none
                        color = Color.currentcolor
                    }
                    ListItemButton {
                        ListItemText {
                            primary = ReactNode(room.name)
                        }
                    }
                }
            }
        }
    }
}
