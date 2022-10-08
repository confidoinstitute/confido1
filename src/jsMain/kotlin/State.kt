package tools.confido.state

import rooms.Room
import rooms.RoomPermission
import tools.confido.refs.*
import users.UserType

class ClientState : GlobalState() {
    var session = UserSession(null, "en")
    fun isAdmin(): Boolean {
        return session.user?.type == UserType.ADMIN
    }

    fun hasPermission(room: Room, permission: RoomPermission): Boolean {
        return room.hasPermission(session.user, permission)
    }
}
val clientState = ClientState()
actual val globalState: GlobalState = clientState