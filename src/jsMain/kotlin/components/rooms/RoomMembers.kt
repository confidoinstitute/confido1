package components.rooms

import components.AppStateContext
import components.UserAvatar
import kotlinx.browser.window
import react.*
import mui.material.*
import org.w3c.dom.HTMLInputElement
import react.dom.events.ChangeEvent
import rooms.RoomPermission

val RoomMembers = FC<Props> {
    val clientAppState = useContext(AppStateContext)
    val state = clientAppState.state
    val stale = clientAppState.stale
    val room = useContext(RoomContext)


    List {
        room.members.map {membership ->
            ListItem {
                key = membership.user.id
                ListItemAvatar {
                    UserAvatar {
                        user = membership.user
                    }
                }
                ListItemText {
                    primary = ReactNode(membership.user.nick ?: "Anonymous")
                    membership.user.email?.let {
                        secondary = ReactNode(it)
                    }
                }
                if (state.hasPermission(room, RoomPermission.MANAGE_MEMBERS)) {
                    FormControl {
                        Select {
                            this.size = Size.small
                            value = membership.role.id.asDynamic()
                            disabled = stale
                            onChange = { event, _ ->
                                window.alert("${membership.user} permission to ${event.target.value}")
                            }
                            // TODO a global list, preferably near room membership definition?
                            listOf("forecaster" to "Forecaster", "moderator" to "Moderator").map {(id, name) ->
                                MenuItem {
                                    value = id
                                    +name
                                }
                            }
                        }
                }
                } else {
                    Typography {
                        +membership.role.name
                    }
                }
            }
        }
    }

    if (state.hasPermission(room, RoomPermission.MANAGE_MEMBERS)) {
        NewInvite {}
    }
}
