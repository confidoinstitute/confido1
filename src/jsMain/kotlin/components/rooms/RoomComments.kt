package components.rooms

import components.AppStateContext
import components.Comment
import components.CommentInput
import components.CommentInputVariant
import react.*
import tools.confido.question.Comment
import tools.confido.refs.ref
import tools.confido.utils.unixNow
import users.User


val RoomComments = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    CommentInput {
        variant = CommentInputVariant.ROOM
        id = room.id
    }

    appState.roomComments[room.ref]?.entries?.sortedBy { it.value.timestamp }?.map {
        Comment {
            this.key = it.key
            this.comment = it.value
        }
    }
}