package components.rooms

import components.AppStateContext
import components.Comment
import components.CommentInput
import components.CommentInputVariant
import react.*
import tools.confido.question.Comment
import tools.confido.utils.unixNow
import users.User


val RoomComments = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    CommentInput {
        variant = CommentInputVariant.ROOM
        id = room.id
    }

    // TODO connect with real comments
    components.rooms.users.map {
        Comment(it.user, unixNow(), "This is my comment.", null)
    }.map {
        Comment {
            this.key = it.key()
            this.comment = it
        }
    }
}