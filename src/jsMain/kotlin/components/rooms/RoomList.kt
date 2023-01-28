package components.rooms

import components.AppStateContext
import components.ListItemNavigation
import icons.AddIcon
import mui.material.*
import react.*
import react.router.useLocation

external interface RoomListProps : Props {
    var newRoomEnabled: Boolean
    var onNavigate: ((String) -> Unit)?
}

val RoomList = FC<RoomListProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val location = useLocation()

    List {
        dense = true
        ListSubheader {
            +"Rooms"
        }
        for (room in appState.rooms.entries) {
            ListItemNavigation {
                this.key = room.key
                to = "/room/${room.key}"
                selected = location.pathname.startsWith(to)
                onNavigate = props.onNavigate

                ListItemText {
                    primary = ReactNode(room.value.name)
                }
            }
        }
        if (props.newRoomEnabled)
        ListItemNavigation {
            this.key = "#create"
            to = "/new_room"
            selected = location.pathname.startsWith(to)
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
