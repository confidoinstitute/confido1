package components.rooms

import components.AppStateContext
import components.ListItemNavigation
import csstype.Color
import csstype.None.none
import emotion.react.css
import hooks.useTraceUpdate
import icons.AddIcon
import mui.material.*
import react.*
import react.router.dom.NavLink

external interface RoomListProps : Props {
    var newRoomEnabled: Boolean
    var onNavigate: ((String) -> Unit)?
}

val RoomList = FC<RoomListProps> { props ->
    val (appState, stale) = useContext(AppStateContext)

    List {
        dense = true
        ListSubheader {
            +"Rooms"
        }
        for (room in appState.rooms) {
            ListItemNavigation {
                this.key = room.id
                to = "/room/${room.id}"
                onNavigate = props.onNavigate

                ListItemText {
                    primary = ReactNode(room.name)
                }
            }
        }
        if (props.newRoomEnabled)
        ListItemNavigation {
            this.key = "#create"
            to = "/new_room"
            onNavigate = props.onNavigate
            disabled = stale

            ListItemIcon {
                AddIcon {}
            }
            ListItemText {
                primary = ReactNode("New room...")
            }
        }
    }
}
