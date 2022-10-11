package tools.confido.state

import rooms.Room
import rooms.RoomPermission
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.question.QuestionComment
import tools.confido.question.RoomComment
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

    override val questionComments: Map<Ref<Question>, Map<String, QuestionComment>>
        get() = TODO("Not yet implemented")
    override val roomComments: Map<Ref<Room>, Map<String, RoomComment>>
        get() = TODO("Not yet implemented")
    override val groupPred: Map<Ref<Question>, Prediction?>
        get() = TODO("Not yet implemented")
}
val clientState = ClientState()
actual val globalState: GlobalState = clientState