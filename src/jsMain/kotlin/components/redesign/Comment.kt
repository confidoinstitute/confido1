package components.redesign

import components.redesign.basic.*
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
    val comment = props.commentInfo.comment
    val author = comment.user.deref() ?: return@FC

    var moreDialogOpen by useState(false)

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

    DialogMenu {
        open = moreDialogOpen
        onClose = { moreDialogOpen = false }
        DialogMenuItem {
            text = "Edit comment"
            icon = EditIcon
            onClick = {
                // TODO: Implement
                showError?.invoke("Editing comments is not currently available.")
                moreDialogOpen = false
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
            this.timestamp = props.commentInfo.comment.timestamp
            this.modified = props.commentInfo.comment.modified
        }

        CommentContents {
            this.content = props.commentInfo.comment.content
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
            FooterAction {
                icon = MoreIcon
                onClick = { moreDialogOpen = true }
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
                FooterAction {
                    icon = DownvoteIcon
                    text = "Downvote"
                    onClick = { downvote() }
                }
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

                    FooterVoteAction {
                        icon = if (voteState == VoteState.DOWNVOTED) {
                            DownvoteActiveIcon
                        } else {
                            DownvoteIcon
                        }
                        onClick = { downvote() }
                    }
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
            fontSize = 15.px
            lineHeight = 18.px
        }
        TextWithLinks { text = props.content }
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
