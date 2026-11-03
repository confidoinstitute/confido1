package tools.confido.web.components.rooms

import tools.confido.web.components.basic.Stack
import tools.confido.web.components.basic.sansSerif
import tools.confido.web.components.useMyUser
import tools.confido.web.components.useRooms
import web.cssom.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tools.confido.rooms.Room
import tools.confido.rooms.RoomPermission
import tools.confido.state.FeatureFlag
import tools.confido.state.enabled

external interface GroupChildRoomListProps : Props {
    var group: Room
}

/** The "Rooms" view of a group room (its index route, in the slot where an
 *  ordinary room shows its question list): child rooms plus a create entry. */
val GroupChildRoomList = FC<GroupChildRoomListProps> { props ->
    val myUser = useMyUser()
    val children = useRooms().values.filter { it.parentRoom?.id == props.group.id }
    // Creating a room inside the group needs both the global create capability
    // and the group's consent (its ROOM_OWNER); mirrors the server-side check.
    val groupsEnabled = FeatureFlag.ROOM_GROUPS.enabled
    val canCreateInGroup = groupsEnabled && myUser?.type?.isProper() == true &&
        props.group.hasPermission(myUser, RoomPermission.ROOM_OWNER)

    Stack {
        css {
            padding = 20.px
            gap = 8.px
        }
        if (children.isEmpty()) {
            div {
                css {
                    fontFamily = sansSerif
                    fontSize = 14.px
                    color = Color("#888888")
                    padding = Padding(10.px, 0.px)
                }
                +"No rooms in this group yet."
            }
        } else {
            RoomListEntries {
                rooms = children.toList()
                small = false
            }
        }
        if (canCreateInGroup) {
            RoomLink {
                to = "/new_room?group=${props.group.id}"
                small = false
                CreateEntryIcon { size = 48.px }
                span {
                    +"Create a room in this group…"
                }
            }
            div {
                css {
                    fontFamily = sansSerif
                    fontSize = 13.px
                    color = Color("#888888")
                }
                +"You can also add an existing room to the group from that room's settings."
            }
        } else if (groupsEnabled && children.isEmpty()) {
            div {
                css {
                    fontFamily = sansSerif
                    fontSize = 13.px
                    color = Color("#888888")
                }
                +"Rooms are added to the group from each room's settings."
            }
        }
    }
}
