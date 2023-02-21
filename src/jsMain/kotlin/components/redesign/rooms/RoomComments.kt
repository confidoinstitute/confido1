package components.redesign.rooms

import components.redesign.SortButton
import components.redesign.SortType
import components.redesign.basic.Stack
import components.redesign.comments.AddCommentButton
import components.redesign.comments.AddCommentDialog
import components.redesign.comments.Comment
import components.redesign.comments.CommentInputVariant
import components.redesign.forms.Button
import components.rooms.RoomContext
import csstype.*
import emotion.react.css
import hooks.useWebSocket
import payloads.responses.CommentInfo
import payloads.responses.WSData
import react.*
import react.dom.html.ReactHTML.div

external interface RoomCommentsProps : Props {
    var onLoad: (() -> Unit)?
}

val RoomComments = FC<RoomCommentsProps> {props ->
    val room = useContext(RoomContext)
    val roomComments = useWebSocket<Map<String, CommentInfo>>("/state${room.urlPrefix}/comments")

    var loaded by useState(false)
    useEffect(roomComments) {
        if (!loaded && roomComments is WSData) {
            loaded = true
            props.onLoad?.invoke()
        }
    }

    var addCommentOpen by useState(false)
    var sortType by useState(SortType.NEWEST)

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

    RoomHeader {
        SortButton {
            this.sortType = sortType
            onChange = { sort -> sortType = sort }
        }

        Button {
            css {
                margin = 0.px
                padding = 7.px
                fontSize = 13.px
                lineHeight = 16.px
                fontWeight = integer(600)
            }
            +"Write a comment"
            onClick = { addCommentOpen = true }
        }
    }

    Stack {
        css {
            flexDirection = FlexDirection.column
            gap = 8.px
            paddingTop = 8.px
            paddingBottom = 8.px
            flexGrow = number(1.0)
            backgroundColor = Color("#f2f2f2")
        }

        val sortedComments = when (sortType) {
            SortType.NEWEST -> roomComments.data?.entries?.sortedByDescending { it.value.comment.timestamp }
            SortType.OLDEST -> roomComments.data?.entries?.sortedBy { it.value.comment.timestamp }
        }

        sortedComments?.map {
            Comment {
                this.commentInfo = it.value
                this.key = it.key
            }
        }
    }

    AddCommentButton {
        onClick = { addCommentOpen = true }
    }
}
