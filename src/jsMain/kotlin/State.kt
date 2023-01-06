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

class ClientState(var sentState: SentState)
    : GlobalState(), BaseState by sentState {
    val session by sentState::session
    val myPredictions by sentState::myPredictions
}

var clientState: ClientState = ClientState(SentState())
actual val globalState: GlobalState get() = clientState

fun Room.havePermission(permission: RoomPermission): Boolean {
    val myself = clientState.session.user
    return hasPermission(myself, permission)
}