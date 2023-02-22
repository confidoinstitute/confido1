package components.redesign.comments

import components.AppStateContext
import components.redesign.*
import components.redesign.basic.*
import components.rooms.RoomContext
import components.showError
import csstype.*
import emotion.react.css
import hooks.useTimeAgo
import io.ktor.http.*
import payloads.responses.CommentInfo
import react.*
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import rooms.RoomPermission
import tools.confido.question.Prediction
import tools.confido.question.QuestionComment
import tools.confido.refs.deref
import users.User
import utils.runCoroutine
import utils.toDateTime

private enum class VoteState {
    NO_VOTE, UPVOTED, DOWNVOTED,
}

external interface CommentProps : Props {
    var commentInfo: CommentInfo
}

val Comment = FC<CommentProps> { props ->
    val room = useContext(RoomContext)
    val (appState, stale) = useContext(AppStateContext)

    val comment = props.commentInfo.comment
    val author = comment.user.deref() ?: return@FC
    val isAuthor = author == appState.session.user

    var moreDialogOpen by useState(false)
    var editDialogOpen by useState(false)

    // TODO: backend support for downvotes
    val voteState = if (props.commentInfo.likedByMe) {
        VoteState.UPVOTED
    } else {
        VoteState.NO_VOTE
    }

    fun upvote() {
        runCoroutine {
            val url = "${comment.urlPrefix}/like"
            val newState = voteState != VoteState.UPVOTED
            Client.sendData(url, newState, onError = { showError?.invoke(it) }) {}
        }
    }

    // TODO: Implement on backend
    fun downvote() {
        showError?.invoke("Downvoting is not currently available")
    }

    fun delete() = runCoroutine {
        val url = comment.urlPrefix
        Client.send(url, method = HttpMethod.Delete, onError = { showError?.invoke(it) }) {}
    }

    EditCommentDialog {
        open = editDialogOpen
        onClose = { editDialogOpen = false }
        this.comment = comment
    }

    DialogMenu {
        open = moreDialogOpen
        onClose = { moreDialogOpen = false }
        if (isAuthor) {
            DialogMenuItem {
                text = "Edit comment"
                icon = EditIcon
                onClick = {
                    editDialogOpen = true
                    moreDialogOpen = false
                }
            }
        }
        DialogMenuItem {
            text = "Delete comment"
            icon = BinIcon
            variant = DialogMenuItemVariant.dangerous
            onClick = {
                delete()
                moreDialogOpen = false
            }
        }
    }

    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.column
            background = Color("#FFFFFF")
            fontFamily = FontFamily.sansSerif
        }

        CommentHeader {
            this.author = author
            this.timestamp = comment.timestamp
            this.modified = comment.modified
        }

        CommentContents {
            this.content = comment.content
        }

        when (comment) {
            is QuestionComment -> {
                if (comment.prediction != null) {
                    AttachedPredictionSection {
                        prediction = comment.prediction
                    }
                }
            }
            else -> {}
        }

        // Actions
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                justifyContent = JustifyContent.flexEnd
                fontWeight = integer(500)
                padding = Padding(0.px, 5.px)
                gap = 10.px
                fontSize = 12.px
                lineHeight = 14.px
            }

            val moreDialogVisible = isAuthor || appState.hasPermission(room, RoomPermission.MANAGE_COMMENTS)
            if (moreDialogVisible) {
                FooterAction {
                    icon = MoreIcon
                    onClick = { moreDialogOpen = true }
                }
            }
            // TODO: Implement replies on backend
            /*
            FooterAction {
                icon = ReplyIcon
                text = "Reply"
            }
            */
            if (props.commentInfo.likeCount == 0 && voteState == VoteState.NO_VOTE) {
                FooterAction {
                    icon = UpvoteIcon
                    text = "Upvote"
                    onClick = { upvote() }
                }
                // TODO: Implement downvotes or remove
                /*
                FooterAction {
                    icon = DownvoteIcon
                    text = "Downvote"
                    onClick = { downvote() }
                }
                */
            } else {
                // Voting
                div {
                    css {
                        display = Display.flex
                        flexDirection = FlexDirection.row
                        justifyContent = JustifyContent.center
                        alignItems = AlignItems.center
                        padding = 10.px
                        gap = 12.px
                    }
                    FooterVoteAction {
                        icon = if (voteState == VoteState.UPVOTED) {
                            UpvoteActiveIcon
                        } else {
                            UpvoteIcon
                        }
                        onClick = { upvote() }
                    }

                    +"${props.commentInfo.likeCount}"

                    // TODO: Implement downvotes or remove
                    /*
                    FooterVoteAction {
                        icon = if (voteState == VoteState.DOWNVOTED) {
                            DownvoteActiveIcon
                        } else {
                            DownvoteIcon
                        }
                        onClick = { downvote() }
                    }
                    */
                }
            }
        }
    }
}

external interface CommentHeaderProps : Props {
    var author: User
    var timestamp: Int
    var modified: Int?
}

val CommentHeader = FC<CommentHeaderProps> { props ->
    val timeAgo = useTimeAgo(props.timestamp)

    div {
        css {
            padding = Padding(15.px, 15.px, 10.px)
            display = Display.flex
            flexDirection = FlexDirection.row
            alignItems = AlignItems.center
            gap = 8.px
        }

        // Avatar
        // TODO: Proper avatar
        Circle {
            color = Color("#45AFEB")
            size = 32.px
        }

        // Name and time
        div {
            val nameColor = Color("#777777")
            css {
                padding = Padding(15.px, 15.px, 10.px)
                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = AlignItems.center
                padding = 0.px
                gap = 5.px
                color = nameColor
                fontSize = 12.px
                lineHeight = 14.px
            }
            span {
                css {
                    fontWeight = integer(600)
                }
                +(props.author.nick ?: "Anonymous")
            }
            Circle {
                color = nameColor
                size = 3.px
            }
            span {
                title = props.timestamp.toDateTime()
                props.modified?.let {
                    title += ", edited ${it.toDateTime()}"
                }

                timeAgo?.let {
                    if (props.modified != null) {
                        +"$it (edited)"
                    } else {
                        +it
                    }
                }
            }
        }
    }
}

external interface CommentContentsProps : Props {
    var content: String
}

val CommentContents = FC<CommentContentsProps> { props ->
    div {
        css {
            padding = Padding(0.px, 15.px)
            color = Color("#000000")
            fontFamily = FontFamily.sansSerif
            fontSize = 15.px
            lineHeight = 18.px
        }
        TextWithLinks { text = props.content }
    }
}

external interface CommentAttachedPredictionProps : Props {
    var prediction: Prediction
}

private val AttachedPredictionSection = FC<CommentAttachedPredictionProps> { props ->
    var expanded by useState(false)
    if (!expanded) {
        div {
            css {
                padding = Padding(10.px, 15.px)
                color = Color("#000000")
                fontFamily = FontFamily.sansSerif
                fontSize = 15.px
                lineHeight = 18.px
            }
            button {
                css {
                    all = Globals.unset
                    cursor = Cursor.pointer

                    color = Color("#6319FF") // primary
                }
                +"See estimate"
                onClick = { expanded = true }
            }
        }
    } else {
        div {
            css {
                padding = Padding(10.px, 0.px)
            }
            // TODO: Show the prediction
            +"TODO visualise prediction"
        }
    }
}

external interface CircleProps : PropsWithClassName {
    /** The size of the circle. Defaults to 16px. */
    var size: Length?

    /** The color of the circle. Defaults to #000000. */
    var color: Color?
}

private val Circle = FC<CircleProps> { props ->
    val size = props.size ?: 16.px
    val color = props.color ?: Color("#000000")

    div {
        this.className = props.className
        css {
            width = size
            height = size
            backgroundColor = color
            borderRadius = 50.pct
            flex = None.none
        }
    }
}

private val MoreIcon = FC<Props> { props ->
    div {
        css {
            width = 15.px
            height = 15.px
            display = Display.flex
            alignItems = AlignItems.center
            gap = 3.px
        }
        Circle {
            size = 3.px
        }
        Circle {
            size = 3.px
        }
        Circle {
            size = 3.px
        }
    }
}

external interface FooterActionProps : Props {
    var text: String?
    var icon: ComponentType<PropsWithClassName>?
    var onClick: (() -> Unit)?
}

private val FooterAction = FC<FooterActionProps> { props ->
    button {
        css {
            all = Globals.unset
            cursor = Cursor.pointer

            display = Display.flex
            flexDirection = FlexDirection.row
            alignItems = AlignItems.center
            gap = 8.px
            padding = 10.px
        }
        props.icon?.let {
            +it.create {
                css {
                    width = 15.px
                    height = 15.px
                }
            }
        }
        props.text?.let {
            +it
        }

        onClick = { props.onClick?.invoke() }
    }
}

external interface FooterVoteActionProps : Props {
    var icon: ComponentType<PropsWithClassName>?
    var onClick: (() -> Unit)?
}

private val FooterVoteAction = FC<FooterActionProps> { props ->
    button {
        css {
            all = Globals.unset
            cursor = Cursor.pointer
        }
        props.icon?.let {
            +it.create {
                css {
                    width = 14.px
                    height = 15.px
                }
            }
        }

        onClick = { props.onClick?.invoke() }
    }
}
