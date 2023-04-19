package components.redesign.rooms

import components.*
import components.redesign.*
import components.redesign.basic.*
import components.redesign.comments.*
import components.redesign.comments.Comment
import components.redesign.comments.CommentInputVariant
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.rooms.*
import csstype.*
import emotion.react.*
import hooks.*
import payloads.responses.*
import react.*
import react.dom.html.ReactHTML.div
import rooms.*

val RoomComments = FC<Props> {
    val (appState, _) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val layoutMode = useContext(LayoutModeContext)
    val roomComments = useWebSocket<Map<String, CommentInfo>>("/state${room.urlPrefix}/comments")

    var addCommentOpen by useState(false)
    var sortType by useState(SortType.NEWEST)

    AddCommentDialog {
        open = addCommentOpen
        onClose = { addCommentOpen = false }
        id = room.id
        variant = CommentInputVariant.ROOM
    }

    // TODO: Alert to disconnection
    //if (roomComments is WSError) {
    //    Alert {
    //        severity = AlertColor.error
    //        +roomComments.prettyMessage
    //    }
    //}

    RoomHeader {
        SortButton {
            options = listOf(SortType.NEWEST, SortType.OLDEST)
            this.sortType = sortType
            onChange = { sort -> sortType = sort }
        }

        if (appState.hasPermission(room, RoomPermission.POST_ROOM_COMMENT)) {
            RoomHeaderButton {
                +"Write a comment"
                onClick = { addCommentOpen = true }
            }
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
            maxWidth = layoutMode.contentWidth
            marginLeft = Auto.auto
            marginRight = Auto.auto
        }
        when (roomComments) {
            is WSData -> {
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
            else -> {
                div {
                    css {
                        padding = Padding(5.px, 15.px)
                        fontFamily = sansSerif
                        fontSize = 15.px
                    }
                    +"Loading the discussion..."
                }
            }
        }
    }

    if (appState.hasPermission(room, RoomPermission.POST_ROOM_COMMENT)) {
        AddCommentButton {
            onClick = { addCommentOpen = true }
        }
    }
}
