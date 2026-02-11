package components.redesign.comments

import Client
import components.*
import components.redesign.*
import components.redesign.TextWithLinks
import components.redesign.basic.*
import components.redesign.forms.*
import components.redesign.questions.ShortenedTextExpanderClass
import components.redesign.questions.predictions.PredictionGraph
import components.redesign.rooms.RoomContext
import web.cssom.*
import emotion.react.*
import hooks.*
import io.ktor.http.*
import payloads.responses.*
import react.*
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import rooms.*
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.*
import tools.confido.refs.*
import users.*
import utils.*
import web.cssom.LineStyle.Companion.solid

private enum class VoteState {
    NO_VOTE, UPVOTED, DOWNVOTED,
}

external interface CommentProps : Props {
    var comment: CommentPresentation<CommentExtraData>
}

val Comment = FC<CommentProps> { props ->
    val comment = props.comment
    val room = use(RoomContext)!!
    val (appState, stale) = use(AppStateContext)!!

    var moreDialogOpen by useState(false)
    var editDialogOpen by useState(false)

    // TODO: backend support for downvotes
    val voteState = if (props.comment.likedByMe) {
        VoteState.UPVOTED
    } else {
        VoteState.NO_VOTE
    }

    fun upvote() {
        runCoroutine {
            val url = "${comment.urlPrefix}/like"
            val newState = voteState != VoteState.UPVOTED
            Client.sendData(url, newState, onError = { showError(it) }) {}
        }
    }

    // TODO: Implement on backend
    fun downvote() {
        showError("Downvoting is not currently available")
    }

    fun delete() = runCoroutine {
        val url = comment.urlPrefix
        Client.send(url, method = HttpMethod.Delete, onError = { showError(it) }) {}
    }

    EditCommentDialog {
        open = editDialogOpen
        onClose = { editDialogOpen = false }
        this.comment = comment
    }

    DialogMenu {
        open = moreDialogOpen
        onClose = { moreDialogOpen = false }
        if (comment.isMine) {
            DialogMenuItem {
                text = "Edit comment"
                icon = EditIcon
                onClick = {
                    editDialogOpen = true
                    moreDialogOpen = false
                }
            }
        }
        if (comment.isMine || appState.hasPermission(room, RoomPermission.MANAGE_COMMENTS))
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
            fontFamily = sansSerif
            if (comment.isMine) {
                backgroundColor = Color("#ebfaff")
                borderLeft = Border(4.px, solid, Color("#72cde9"))
                marginLeft = -4.px
            }
        }

        CommentHeader {
            this.author = comment.user
            this.timestamp = comment.timestamp
            this.modified = comment.modified
        }

        CommentContents {
            this.content = comment.content
        }

        when (comment.extraData) {
            is QuestionCommentExtraData -> {
                comment.extraData.dist?.let {
                    AttachedPredictionSection {
                        dist = it
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

            val moreDialogVisible = comment.isMine || appState.hasPermission(room, RoomPermission.MANAGE_COMMENTS)
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
            if (props.comment.likeCount == 0 && voteState == VoteState.NO_VOTE) {
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

                    +"${props.comment.likeCount}"

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
    var author: UserPresentation
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
            color = props.author.color?.let { Color(it) }
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
                +props.author.displayName
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
            fontFamily = sansSerif
            fontSize = 15.px
            lineHeight = 18.px
            whiteSpace = WhiteSpace.preLine
        }
        SeeMore {
            maxLines = 10
            lineHeight = 18.0
            linkCss = ShortenedTextExpanderClass
            backgroundColor = NamedColor.white

            TextWithLinks { text = props.content }
        }
    }
}

external interface CommentAttachedPredictionProps : Props {
    var dist: ProbabilityDistribution
}

private val AttachedPredictionSection = FC<CommentAttachedPredictionProps> { props ->
    var expanded by useState(false)
    if (!expanded) {
        div {
            css {
                padding = Padding(10.px, 15.px)
                color = Color("#000000")
                fontFamily = sansSerif
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
            PredictionGraph {
                key = "${props.key}-vis"
                space = props.dist.space
                dist = props.dist
                isGroup = false
            }
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
    var icon: ComponentType<IconProps>?
    var onClick: (() -> Unit)?
}

private val FooterAction = FC<FooterActionProps> { props ->
    ButtonBase {
        css {
            background = None.none
            display = Display.flex
            flexDirection = FlexDirection.row
            alignItems = AlignItems.center
            gap = 8.px
            padding = 10.px

            ".ripple" {
                backgroundColor = Color("#000000")
            }
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
    var icon: ComponentType<IconProps>?
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
