package components.redesign.rooms

import components.redesign.comments.AddCommentButton
import components.redesign.comments.AddCommentDialog
import components.redesign.comments.Comment
import components.redesign.comments.CommentInputVariant
import components.redesign.forms.TextButton
import components.rooms.RoomContext
import csstype.*
import emotion.react.css
import hooks.useWebSocket
import payloads.responses.CommentInfo
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useContext
import react.useState

val RoomComments = FC<Props> {
    val room = useContext(RoomContext)
    val roomComments = useWebSocket<Map<String, CommentInfo>>("/state${room.urlPrefix}/comments")

    var addCommentOpen by useState(false)

    AddCommentDialog {
        open = addCommentOpen
        onClose = { addCommentOpen = false }
        id = room.id
        variant = CommentInputVariant.ROOM
    }

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
        roomComments.data?.entries?.sortedByDescending { it.value.comment.timestamp }?.map {
            Comment {
                this.commentInfo = it.value
                this.key = it.key
            }
        }

        AddCommentButton {
            onClick = { addCommentOpen = true }
        }
    }
}
