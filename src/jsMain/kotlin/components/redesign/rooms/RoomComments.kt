package components.redesign.rooms

import components.redesign.*
import components.redesign.basic.*
import components.redesign.comments.*
import components.rooms.*
import csstype.*
import emotion.react.*
import hooks.*
import payloads.responses.*
import react.*
import react.dom.html.ReactHTML.div

external interface RoomCommentsProps : Props {
    var onLoad: (() -> Unit)?
}

val RoomComments = FC<RoomCommentsProps> {props ->
    val room = useContext(RoomContext)
    val roomComments = useWebSocket<Map<String, CommentInfo>>("/state${room.urlPrefix}/comments")

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
            options = listOf(SortType.NEWEST, SortType.OLDEST)
            this.sortType = sortType
            onChange = { sort -> sortType = sort }
        }

        RoomHeaderButton {
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
            else -> emptyList()
        }

        sortedComments?.map {
            Comment {
                this.commentInfo = it.value
                this.key = it.key
            }
        } ?: div {
            css {
                height = 100.vh
            }
        }
    }

    AddCommentButton {
        onClick = { addCommentOpen = true }
    }
}
