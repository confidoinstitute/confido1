package components

import Client
import components.rooms.RoomContext
import csstype.AlignItems
import csstype.number
import csstype.rem
import dom.html.HTMLElement
import dom.html.HTMLInputElement
import hooks.useCoroutineLock
import icons.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.js.jso
import mui.lab.LoadingButton
import mui.lab.LoadingPosition
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import payloads.requests.CreateComment
import payloads.responses.CommentInfo
import react.*
import react.dom.html.ButtonType
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.strong
import react.dom.onChange
import rooms.RoomPermission
import tools.confido.question.Prediction
import tools.confido.question.QuestionComment
import tools.confido.question.RoomComment
import tools.confido.refs.deref
import tools.confido.refs.eqid
import tools.confido.utils.unixNow
import utils.*
import web.timers.clearInterval
import web.timers.setInterval
import web.timers.setTimeout
import kotlin.coroutines.EmptyCoroutineContext

external interface CommentManageMenuProps : Props {
    var disabled: Boolean
    var onEdit: (() -> Unit)?
    var onDelete: (() -> Unit)?
}

val CommentManageMenu = FC<CommentManageMenuProps> {props ->
    var menuAnchor by useState<HTMLElement?>(null)
    var menuOpen by useState(false)

    IconButton {
        disabled = props.disabled
        onClick = { menuOpen = true; menuAnchor = it.currentTarget }
        MoreVertIcon {}
    }
    Menu {
        anchorEl = menuAnchor.asDynamic()
        open = menuOpen
        onClose = {menuOpen = false}
        MenuItem {
            onClick = {props.onEdit?.invoke(); menuOpen = false}
            disabled = props.disabled
            ListItemIcon {
                EditIcon {}
            }
            ListItemText {
                +"Edit…"
            }
        }
        MenuItem {
            onClick = { props.onDelete?.invoke(); menuOpen = false }
            disabled = props.disabled
            ListItemIcon {
                DeleteIcon {}
            }
            ListItemText {
                +"Delete"
            }
        }
    }
}

external interface CommentProps : Props {
    var commentInfo: CommentInfo
}

val Comment = FC<CommentProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val currentUser = appState.session.user
    val comment = props.commentInfo.comment
    val user = comment.user.deref() ?: return@FC

    var textAgo by useState("")

    val canManage =
        (user eqid currentUser || appState.hasPermission(room, RoomPermission.MANAGE_COMMENTS)) && !stale
    var editMode by useState(false)
    var editContent by useState("")

    fun deleteComment() = runCoroutine {
        val url = comment.urlPrefix
        Client.send(url, method = HttpMethod.Delete, onError = {showError(it)}) {}
    }

    fun editCommentMode() {
        editMode = true
        editContent = comment.content
    }

    val editSubmit = useCoroutineLock()

    fun editComment() = editSubmit {
        val url = "${comment.urlPrefix}/edit"
        Client.sendData(url, editContent, onError = {showError(it)}) { editMode = false }
    }

    val liked = props.commentInfo.likedByMe

    useEffect(comment.timestamp) {
        fun setText() {
            textAgo = durationAgo(unixNow() - comment.timestamp)
        }
        setText()
        val interval = setInterval(::setText, 5000)

        cleanup {
            clearInterval(interval)
        }
    }

    Card {
        sx {
            marginTop = themed(2)
            marginBottom = themed(2)
        }
        CardHeader {
            val name = user.nick ?: "Anonymous"
            title = ReactNode(name)
            subheader = Tooltip.create {
                    this.title = ReactNode(comment.timestamp.toDateTime() + if (comment.modified != null) ", edited ${comment.modified?.toDateTime() ?: ""}" else "")
                    span {
                        +textAgo
                        if (comment.modified != null) +" (edited)"
                    }
                }
            avatar = UserAvatar.create {
                this.user = user
            }
            action = Fragment.create {
                if (canManage) {
                    CommentManageMenu {
                        disabled = stale
                        onEdit = ::editCommentMode
                        onDelete = ::deleteComment
                    }
                }
            }
        }
        CardContent {
            if (editMode)
                //ClickAwayListener {
                    //onClickAway = {editMode = false}
                    form {
                        onSubmit = { it.preventDefault(); editComment() }
                        TextField {
                            inputRef =
                                { el: HTMLInputElement? -> setTimeout({ el?.focus() }, 0) }.asDynamic()
                            variant = FormControlVariant.standard
                            fullWidth = true
                            value = editContent
                            onChange = { editContent = it.eventValue() }
                            onKeyDown = { if (it.key == "Escape") editMode = false; it.stopPropagation() }
                            this.asDynamic().InputProps = jso<InputProps> {
                                startAdornment = InputAdornment.create {
                                    position = InputAdornmentPosition.start
                                    IconButton {
                                        onClick = {editMode = false}
                                        CloseIcon {}
                                    }
                                }
                                endAdornment = InputAdornment.create {
                                    position = InputAdornmentPosition.end
                                    IconButton {
                                        type = ButtonType.submit
                                        disabled = stale || editContent.isEmpty() || editSubmit.running
                                        SendIcon {}
                                    }
                                }
                            }
                        }
                    }
                //}
            else
                TextWithLinks { text = comment.content }
        }
        if (comment is QuestionComment && comment.prediction != null) {
            Divider {}
            CardContent {
                Typography {
                    sx {
                        lineHeight = number(2.0)
                    }
                    strong {
                        +"Prediction: "
                    }
                    SpoilerButton {
                        DistributionSummary {
                            allowPlotDialog = true
                            distribution = comment.prediction.dist
                        }
                    }
                }
            }
        }
        CardActions {
            IconButton {
                Badge {
                    badgeContent = (props.commentInfo.likeCount).let {
                        if (it > 0) ReactNode(it.toString())
                        else null
                    }
                    color = BadgeColor.secondary
                    if (liked)
                        ThumbUpIcon {}
                    else
                        ThumbUpOutlineIcon{}
                }
                disabled = stale
                onClick = {runCoroutine {
                    val url = "${comment.urlPrefix}/like"
                    Client.sendData(url, !liked, onError = {showError(it)}) {}
                }
                }
            }
        }
    }
}

enum class CommentInputVariant {
    QUESTION,
    ROOM,
}

external interface CommentInputProps : Props {
    var variant: CommentInputVariant
    var id: String
    var prediction: Prediction?
    var onSubmit: ((CreateComment) -> Unit)?
}

val CommentInput = FC<CommentInputProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    var content by useState("")
    var attachPrediction by useState(false)

    val room = useContext(RoomContext)

    val submit = useCoroutineLock()

    form {
        onSubmit = {
            it.preventDefault()
            submit {
                val createdComment = CreateComment(unixNow(), content, attachPrediction)
                val url = when(props.variant) {
                    CommentInputVariant.QUESTION -> "${questionUrl(props.id)}/comments/add"
                    CommentInputVariant.ROOM -> "${roomUrl(props.id)}/comments/add"
                }

                Client.sendData(url, createdComment, onError = {showError(it)}) {
                    content = ""
                    props.onSubmit?.invoke(createdComment)
                }
            }
        }

        fun commentInput(childrenBuilder: ChildrenBuilder) = childrenBuilder.apply {
            TextField {
                fullWidth = true
                this.placeholder = "Write a comment..."
                this.margin = FormControlMargin.none
                this.name = "content"
                this.value = content
                this.onChange = { content = it.eventValue() }
            }
        }

        fun sendButton(childrenBuilder: ChildrenBuilder) = childrenBuilder.apply {
            LoadingButton {
                +"Send"
                disabled = submit.running || content.isEmpty() || stale
                type = ButtonType.submit
                loading = submit.running
                loadingPosition = LoadingPosition.end
                endIcon = SendIcon.create()
            }
        }

        when(props.variant) {
            CommentInputVariant.QUESTION -> {
                DialogActions {
                    commentInput(this)
                }
                DialogActions {
                    if (room.hasPermission(appState.session.user, RoomPermission.SUBMIT_PREDICTION))
                    FormGroup {
                        sx {
                            flexGrow = number(1.0)
                        }
                        FormControlLabel {
                            label = span.create {
                                +"Attach prediction "
                                props.prediction?.let {
                                    Typography {
                                        variant = TypographyVariant.body2
                                        +it.dist.description
                                    }
                                }
                            }
                            control = Checkbox.create {
                                this.name = "attach"
                                this.checked = attachPrediction
                                this.disabled = props.prediction == null
                                this.onChange = { _, value -> attachPrediction = value }
                            }
                        }
                    }
                    sendButton(this)
                }
            }
            CommentInputVariant.ROOM -> {
                Stack {
                    this.direction = responsive(StackDirection.row)
                    this.spacing = themed(2)
                    sx {
                        alignItems = AlignItems.baseline
                    }
                    commentInput(this)
                    sendButton(this)
                }
            }
        }
    }
}

val CommentSkeleton = FC<Props> {
    Card {
        sx {
            marginTop = themed(2)
            marginBottom = themed(2)
        }
        CardHeader {
            title = Skeleton.create {
                width = 15.rem
            }
            subheader = Skeleton.create {
                width = 3.rem
            }
            avatar = Skeleton.create {
                variant = SkeletonVariant.circular
                width = 40
                height = 40
            }
        }
        CardContent {
            Skeleton { }
        }
        CardActions {
            IconButton {
                ThumbUpOutlineIcon{}
                disabled = true
            }
        }
    }
}