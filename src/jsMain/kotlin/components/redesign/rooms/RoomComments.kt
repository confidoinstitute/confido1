package components.redesign.rooms

import components.redesign.Comment
import components.rooms.RoomContext
import csstype.*
import emotion.react.css
import hooks.useWebSocket
import payloads.responses.CommentInfo
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useContext

val RoomComments = FC<Props> {
    val room = useContext(RoomContext)

    val roomComments = useWebSocket<Map<String, CommentInfo>>("/state${room.urlPrefix}/comments")

    // TODO: Comment creation input

    // TODO: Alert to disconnection
    //if (roomComments is WSError) {
    //    Alert {
    //        severity = AlertColor.error
    //        +roomComments.prettyMessage
    //    }
    //}

    // TODO: Progress while loading
    //if (roomComments is WSLoading) {
    //    CircularProgress {
    //    }
    //}

    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.column
            gap = 8.px
            marginTop = 8.px
        }
        roomComments.data?.entries?.sortedBy { it.value.comment.timestamp }?.map {
            Comment {
                this.commentInfo = it.value
            }
        }
    }
}
