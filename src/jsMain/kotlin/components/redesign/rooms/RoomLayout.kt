package components.redesign.rooms

import Client
import components.AppStateContext
import components.redesign.*
import components.redesign.basic.*
import components.redesign.forms.Button
import components.redesign.layout.LayoutModeContext
import components.redesign.rooms.dialog.CsvExportDialog
import components.redesign.rooms.dialog.EditRoomSettingsDialog
import web.cssom.*
import web.html.*
import emotion.css.ClassName
import emotion.react.*
import hooks.*
import io.ktor.http.*
import js.objects.unsafeJso as jso
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.span
import react.router.*
import rooms.*
import components.redesign.presentation.PresentationTab
import payloads.requests.CreatePresentation
import tools.confido.extensions.ClientExtension
import tools.confido.refs.*
import tools.confido.state.clientState
import tools.confido.utils.mapDeref
import utils.roomUrl
import utils.runCoroutine
import web.animations.requestAnimationFrame
import web.events.Event
import web.events.EventType
import web.events.addEventHandler
import web.events.addEventListener
import web.events.removeEventListener
import web.scroll.smooth
import web.timers.*
import web.window.window

/** Banner shown in a room when a presentation is running, allowing the user to follow/unfollow. */
private val PresentationFollowBanner = FC<Props> {
    val (appState, stale) = use(AppStateContext)!!
    val room = use(RoomContext)!!
    val isFollowing = appState.session.followingPresentation?.id == room.id
    val presentationRunning = room._presentationRunning == true
    val isGuest = appState.session.user?.type == users.UserType.GUEST
    // Don't show anything if no presentation is running
    if (!presentationRunning && !isFollowing) return@FC
    // Don't show to moderators who manage the presentation
    if (appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS)) return@FC
    // Hide the "stop following" banner for guests (they follow via invite link and shouldn't be confused)
    if (isFollowing && isGuest) return@FC

    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.row
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            gap = 12.px
            padding = Padding(10.px, 16.px)
            fontFamily = sansSerif
            fontSize = 14.px
            lineHeight = 17.px
            if (isFollowing) {
                backgroundColor = Color("#E8F5E9")
                color = Color("#2E7D32")
                borderBottom = Border(1.px, LineStyle.solid, Color("#C8E6C9"))
            } else {
                backgroundColor = Color("#FFF3E0")
                color = Color("#E65100")
                borderBottom = Border(1.px, LineStyle.solid, Color("#FFE0B2"))
            }
        }

        span {
            css { flexGrow = number(1.0); textAlign = TextAlign.center }
            if (isFollowing) {
                +"Following presentation"
            } else {
                +"A presentation is running in this room"
            }
        }

        Button {
            css {
                margin = 0.px
                padding = Padding(5.px, 12.px)
                fontSize = 13.px
                lineHeight = 16.px
                fontWeight = integer(600)
                minWidth = Auto.auto
            }
            disabled = stale
            onClick = {
                runCoroutine {
                    if (isFollowing) {
                        Client.send("${room.urlPrefix}/presentation/unfollow", method = HttpMethod.Post,
                            onError = { showError(it) }) {}
                    } else {
                        Client.send("${room.urlPrefix}/presentation/follow", method = HttpMethod.Post,
                            onError = { showError(it) }) {}
                    }
                }
            }
            if (isFollowing) {
                +"Stop following"
            } else {
                +"Follow presentation"
            }
        }
    }
}

/** Simple component that redirects to a relative path. Used for first-available-tab redirect. */
private external interface RedirectToProps : Props {
    var to: String
}
private val RedirectTo = FC<RedirectToProps> { props ->
    val navigate = useNavigate()
    useEffect(props.to) {
        navigate(props.to, jso<dynamic> { replace = true })
    }
}

val RoomHeaderButton = Button.withStyle {
        margin = 0.px
        padding = 7.px
        fontSize = 13.px
        lineHeight = 16.px
        fontWeight = integer(600)
}

external interface RoomHeaderProps: PropsWithChildren, PropsWithClassName {
    var fullWidth: Boolean?
    var innerClass: ClassName?
}
val RoomHeader = FC<RoomHeaderProps> { props ->
    val layoutMode = use(LayoutModeContext)!!
    Stack {
        direction = FlexDirection.row
        css(ClassName("room-header"), override=props) {
            backgroundColor = Color("#FFFFFF")
            borderBottom = Border(0.5.px, LineStyle.solid, Color("#CCCCCC"))
            padding = Padding(15.px, 14.px, 15.px, 15.px)
            position = Position.sticky
            top = 76.px
            zIndex = integer(20)
            justifyContent = JustifyContent.center
        }

        Stack {
            direction = FlexDirection.row
            css(ClassName("room-header-inner"), override=props.innerClass) {
                width = if (props.fullWidth ?: false) 100.pct else layoutMode.contentWidth
                justifyContent = JustifyContent.spaceBetween
            }
            +props.children
        }
    }
}

external interface RoomLayoutProps : Props {
    var openEdit: Boolean?
    var openSchedule: Boolean?
}

val RoomLayout = FC<RoomLayoutProps> { props->
    val (appState, _) = use(AppStateContext)!!
    val room = use(RoomContext)!!
    val navigate = useNavigate()
    val location = useLocation()

    val size = useElementSize<HTMLDivElement>()
    val tabRef = useRef<HTMLDivElement>()
    val palette = room.color.palette

    var scrollY by useState(0.0)
    val cutoff = 60.0 + size.height - 15.0

    var dialogOpen by useState(false)
    var editOpen by useState(props.openEdit ?: false)
    var exportOpen by useState(false)
    var deleteConfirmOpen by useState(false)

    fun delete() = runCoroutine {
        Client.send(
            roomUrl(room.id),
            HttpMethod.Delete,
            onError = { showError(it) }) {
            navigate("/")
        }
    }

    EditRoomSettingsDialog {
        open = editOpen
        openSchedule = props.openEdit == true && props.openSchedule == true
        onClose = { editOpen = false; if (location.pathname.endsWith("/edit")) navigate("..") }
    }
    CsvExportDialog {
        open = exportOpen
        onClose = { exportOpen = false }
    }

    ConfirmDialog {
        open = deleteConfirmOpen
        onClose = { deleteConfirmOpen = false }
        title = "Delete this room?"
        +"This will result in loss of all questions, predictions, and discussions made in “${room.name}”. This action cannot be undone."
        onConfirm = ::delete
    }

    DialogMenu {
        open = dialogOpen
        onClose = { dialogOpen = false }

        if (appState.hasPermission(room, RoomPermission.ROOM_OWNER)) {
            DialogMenuItem {
                text = "Change settings of this room"
                icon = EditIcon
                onClick = { editOpen = true; dialogOpen = false }
            }
        }
        if (appState.hasPermission(room, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)) {
            DialogMenuItem {
                icon = ExportIcon
                text = "Export to CSV..."
                onClick = { exportOpen = true; dialogOpen = false }
            }
        }

        if (appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS) && room.presentation == null) {
            DialogMenuItem {
                text = "Create presentation"
                icon = PresentationIcon
                onClick = {
                    dialogOpen = false
                    runCoroutine {
                        Client.sendData("${room.urlPrefix}/presentation/create",
                            CreatePresentation(),
                            onError = { showError(it) }) {}
                    }
                }
            }
        }

        if (appState.hasPermission(room, RoomPermission.ROOM_OWNER)) {
            DialogMenuItem {
                text = "Delete this room"
                variant = DialogMenuItemVariant.dangerous
                icon = BinIcon
                onClick = {
                    dialogOpen = false
                    deleteConfirmOpen = true
                }
            }
        }

        DialogMenuSeparator {}

        DialogMenuCommonActions {
            pageName = room.name
            onClose = { dialogOpen = false }
        }
    }

    Header {
        title = room.name
        appBarColor = palette.color
    }

    fun scrollDownTabs() {
        tabRef.current?.apply {
            requestAnimationFrame {
                scrollIntoView(jso {
                    if (scrollY < offsetTop)
                        behavior = web.scroll.ScrollBehavior.smooth
                })
            }
        }
    }

    useEffectOnceWithCleanup {
        val onScroll = { event: Event ->
            scrollY = window.scrollY
        }

        window.addEventHandler(EventType("scroll"), onScroll)
        onCleanup {
            window.removeEventListener(EventType<Event>("scroll"), onScroll)
        }
    }

    RoomNavbar {
        this.palette = palette
        navigateBack = "/"
        span {
            if (scrollY > cutoff)
                +room.name
        }
        onMenu = {
            dialogOpen = true
        }
    }
    Stack {
        component = header
        css {
            backgroundColor = palette.color
            color = palette.text.color
            flexDirection = FlexDirection.column
            paddingBottom = 32.px
        }

        div {
            css {
                top = 44.px
                position = Position.fixed
                width = 100.pct
                height = 30.px
                if (scrollY > 0) {
                    background = linearGradient(stop(palette.color, 0.pct), stop(palette.color.addAlpha(0.0), 100.pct))
                }
                zIndex = integer(1)
                transition = 0.5.s
            }
        }
        div {
            css {
                mask = url("/static/icons/${room.icon}")
                maskSize = MaskSize.contain
                backgroundColor = palette.text.color
                height = 64.px
                width = 64.px
                margin = Margin(0.px, Auto.auto)
            }
        }
        div {
            ref = size.ref
            css {
                marginTop = 16.px
                width = 100.pct
                fontSize = 32.px
                lineHeight = 34.px
                fontWeight = integer(800)
                fontFamily = sansSerif
                textAlign = TextAlign.center
                padding = Padding(0.px, 10.px)
            }
            +room.name
        }
        div {
            css {
                paddingTop = 18.px
                paddingBottom = 100.px
                paddingLeft = 60.px
                paddingRight = 60.px
                textAlign = TextAlign.center
                fontFamily = sansSerif
                fontSize = 16.px
                lineHeight = 19.px
                whiteSpace = WhiteSpace.preLine
            }
            SeeMore {
                maxLines = 5
                lineHeight = 19.0
                backgroundColor = palette.color
                linkCss = ClassName {
                    color = Color("inherit")
                }
                TextWithLinks {
                    text = room.description
                }
            }
            ClientExtension.forEach { it.roomInfoExtra(room, appState, this) }
        }
        div {
            css {
                position = Position.relative
                top = (-44).px
            }
            ref = tabRef
        }
    }
    RoomTabs {
        this.palette = palette
        css {
            position = Position.sticky
            top = 44.px
            marginTop = -32.px
            zIndex = integer(20)
        }
        onChange = ::scrollDownTabs
    }
    PresentationFollowBanner {}
        main {
            css {
                flexGrow = number(1.0)
                display = Display.flex
                flexDirection = FlexDirection.column
            }
            
            val routes = useMemo(room.id, appState.session) {
                buildList {
                    if (appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS)) {
                        add(jso<RouteObject> {
                            index = true
                            element = QuestionList.create {
                                // Use effective questions for display if provided (e.g. aggregated from child rooms),
                                // otherwise use the room's own questions.
                                val effQuestions = room.effectiveQuestions
                                    ?.filter { it.visibleInList }
                                    ?.mapNotNull { it.question.deref() }
                                questions = effQuestions ?: room.questions.mapDeref()
                                showHiddenQuestions = appState.hasPermission(room, RoomPermission.VIEW_HIDDEN_QUESTIONS)
                            }
                        })
                    }
                    if (appState.hasPermission(room, RoomPermission.VIEW_ROOM_COMMENTS)) {
                        add(jso<RouteObject> {
                            path = "discussion"
                            element = RoomComments.create()
                        })
                    }
                    if (appState.hasPermission(room, RoomPermission.MANAGE_MEMBERS)) {
                        add(jso<RouteObject> {
                            path = "members"
                            element = RoomMembers.create()
                        })
                    }
                    if (appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS)) {
                        add(jso<RouteObject> {
                            path = "calibration"
                            element = RoomCalibration.create {
                                this.room = room
                            }
                        })
                    }

                    // Score tab visible when scoring is enabled (not just when scoreboard is visible)
                    if (room.scoring != null) {
                        add(jso<RouteObject> {
                            path = "score"
                            element = RoomScore.create()
                        })
                    }
                    if (appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS)) {
                        add(jso<RouteObject> {
                            path = "manage_questions"
                            element = QuestionManagement.create {
                                questions = room.questions.mapDeref()
                                this.room = room
                            }
                        })
                    }
                    
                    if (appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS) && room.presentation != null) {
                        add(jso<RouteObject> {
                            path = "presentation"
                            element = PresentationTab.create()
                        })
                    }

                    // Add extension routes
                    clientState.extensions.forEach { extension ->
                        addAll(extension.roomRoutesExtra(room))
                    }

                    // If no index route exists (e.g. VIEW_QUESTIONS permission was stripped for group rooms),
                    // redirect to the first available tab.
                    if (none { it.index == true } && isNotEmpty()) {
                        val firstPath = firstNotNullOfOrNull { it.path } ?: ""
                        add(0, jso<RouteObject> {
                            index = true
                            element = RedirectTo.create { to = firstPath }
                        })
                    }
                }.toTypedArray()
            }
            
            +useRoutes(routes)
        }
}
