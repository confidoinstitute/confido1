package tools.confido.state

import rooms.Room
import rooms.RoomPermission
import tools.confido.question.Question
import tools.confido.refs.*
import users.User
import users.UserType

class ClientState(
    override val rooms: Map<String, Room> = emptyMap(),
    override val users: Map<String, User> = emptyMap(),
    override val questions: Map<String, Question> = emptyMap(),
    val session: UserSession = UserSession(),
) : GlobalState() {
    fun isAdmin(): Boolean {
        return session.user?.type == UserType.ADMIN
    }

    fun hasPermission(room: Room, permission: RoomPermission): Boolean {
        return room.hasPermission(session.user, permission)
    }
}
val clientState = ClientState()
actual val globalState: GlobalState = clientState