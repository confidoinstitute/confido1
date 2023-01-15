package components.rooms

import components.AppStateContext
import components.Comment
import components.CommentInput
import components.CommentInputVariant
import hooks.useWebSocket
import mui.material.Alert
import mui.material.AlertColor
import mui.material.CircularProgress
import payloads.responses.CommentInfo
import payloads.responses.WSError
import payloads.responses.WSLoading
import react.*
import tools.confido.question.Comment
import tools.confido.refs.ref
import tools.confido.utils.unixNow
import users.User


val RoomComments = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    val roomComments = useWebSocket<Map<String, CommentInfo>>("/state/rooms/${room.id}/comments")

    CommentInput {
        variant = CommentInputVariant.ROOM
        id = room.id
    }

    if (roomComments is WSError) {
        Alert {
            severity = AlertColor.error
            +roomComments.prettyMessage
        }
    }

    if (roomComments is WSLoading) {
        CircularProgress {
        }
    }

    roomComments.data?.entries?.sortedBy { it.value.comment.timestamp }?.map {
        Comment {
            this.key = it.key
            this.commentInfo = it.value
        }
    }
}