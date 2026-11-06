package tools.confido.web.components.rooms

import tools.confido.web.Client
import tools.confido.web.components.useFollowingPresentation
import tools.confido.web.components.usePermission
import tools.confido.web.components.useMyUser
import tools.confido.web.components.useRooms
import tools.confido.web.components.useSelf
import tools.confido.web.components.useDisconnected
import tools.confido.web.components.*
import tools.confido.web.components.basic.*
import tools.confido.web.components.forms.Button
import tools.confido.web.components.layout.LayoutMode
import tools.confido.web.components.layout.LayoutModeContext
import tools.confido.web.components.rooms.dialog.CsvExportDialog
import tools.confido.web.components.rooms.dialog.EditRoomSettingsDialog
import web.cssom.*
import web.html.*
import emotion.css.ClassName
import emotion.react.*
import tools.confido.web.hooks.*
import tools.confido.payloads.responses.WSData
import tools.confido.refs.ref
import io.ktor.http.*
import js.objects.unsafeJso as jso
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.span
import react.router.*
import tools.confido.rooms.*
import tools.confido.web.components.presentation.PresentationTab
import tools.confido.web.components.presentation.CreatePresentationDialog
import tools.confido.api.Api
import tools.confido.extensions.ClientExtension
import tools.confido.refs.*
import tools.confido.state.Topics
import tools.confido.web.utils.roomUrl
import tools.confido.web.utils.runCoroutine
import web.animations.requestAnimationFrame
import web.events.Event
import web.events.EventType
import web.scroll.smooth
import web.timers.*
import web.window.window

/** Banner shown in a room when a presentation is running, allowing the user to follow/unfollow. */
private val PresentationFollowBanner = FC<Props> {
    val room = use(RoomContext)!!
    val isFollowing = useFollowingPresentation()?.id == room.id
    val presentationRunning = room._presentationRunning == true
    val isGuest = useMyUser()?.type == tools.confido.users.UserType.GUEST
    // Don't show anything if no presentation is running
    if (!presentationRunning && !isFollowing) return@FC
    // Don't show to moderators who manage the presentation
    if (usePermission(room, RoomPermission.MANAGE_QUESTIONS)) return@FC
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
            disabled = useDisconnected()
            onClick = {
                runCoroutine {
                    if (isFollowing) {
                        Client.call(Api.presentationUnfollow(room.ref))
                    } else {
                        Client.call(Api.presentationFollow(room.ref))
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
                width = if (props.fullWidth ?: false) 100.pct else layoutMode.wideContentWidth
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
    react.useEffectOnceWithCleanup { console.log("MOUNT RoomLayout"); onCleanup { console.log("UNMOUNT RoomLayout") } } // XXX TEMP DEBUG
    val room = use(RoomContext)!!
    val navigate = useNavigate()
    val location = useLocation()

    val size = useElementSize<HTMLDivElement>()
    val tabRef = useRef<HTMLDivElement>()
    val palette = room.color.palette
    val layoutMode = use(LayoutModeContext)!!

    var scrollY by useState(0.0)
    val cutoff = 60.0 + size.height - 15.0

    var dialogOpen by useState(false)
    var editOpen by useState(props.openEdit ?: false)
    var exportOpen by useState(false)
    var createPresentationOpen by useState(false)
    var deleteConfirmOpen by useState(false)

    fun delete() = runCoroutine {
        Client.call(Api.roomDelete(room.ref))
        navigate("/")
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
    CreatePresentationDialog {
        open = createPresentationOpen
        this.room = room
        onClose = { createPresentationOpen = false }
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

        if (usePermission(room, RoomPermission.ROOM_OWNER)) {
            DialogMenuItem {
                text = "Change settings of this room"
                icon = EditIcon
                onClick = { editOpen = true; dialogOpen = false }
            }
        }
        if (usePermission(room, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)) {
            DialogMenuItem {
                icon = ExportIcon
                text = "Export to CSV..."
                onClick = { exportOpen = true; dialogOpen = false }
            }
        }

        if (usePermission(room, RoomPermission.MANAGE_QUESTIONS) && room.presentation == null) {
            DialogMenuItem {
                text = "Create presentation"
                icon = PresentationIcon
                onClick = {
                    dialogOpen = false
                    createPresentationOpen = true
                }
            }
        }

        if (usePermission(room, RoomPermission.ROOM_OWNER)) {
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

    useWindowEventListener(EventType<Event>("scroll")) { scrollY = window.scrollY }

    // A child room's back button goes up to its group — but only when the viewer
    // can actually see the group (an implied member of just the child cannot).
    val visibleRooms = useRooms()
    val backTarget = room.parentRoom?.id?.let { visibleRooms[it]?.urlPrefix } ?: "/"

    RoomNavbar {
        this.palette = palette
        navigateBack = backTarget
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
        if (layoutMode >= LayoutMode.TABLET) {
            // Compact header: keep the room color and icon as identity, but stop
            // spending a third of the viewport on it. The description is clamped;
            // SeeMore expands it in place for the rare occasions it is needed.
            div {
                ref = size.ref
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    width = layoutMode.wideContentWidth
                    margin = Margin(0.px, Auto.auto)
                    alignItems = AlignItems.center
                    gap = 14.px
                    padding = Padding(14.px, 10.px, 8.px)
                }
                room.icon?.let { icon ->
                    div {
                        css {
                            mask = url("/static/icons/$icon")
                            maskSize = MaskSize.contain
                            backgroundColor = palette.text.color
                            height = 36.px
                            width = 36.px
                            flexShrink = number(0.0)
                        }
                    }
                }
                div {
                    css {
                        fontSize = 26.px
                        lineHeight = 30.px
                        fontWeight = integer(800)
                        fontFamily = sansSerif
                    }
                    +room.name
                }
            }
            div {
                css {
                    width = layoutMode.wideContentWidth
                    margin = Margin(0.px, Auto.auto)
                    paddingTop = 0.px
                    paddingRight = 10.px
                    paddingBottom = 14.px
                    // Align with the heading text, which sits right of the icon.
                    paddingLeft = if (room.icon != null) 60.px else 10.px
                    fontFamily = sansSerif
                    fontSize = 14.px
                    lineHeight = 18.px
                    whiteSpace = WhiteSpace.preLine
                }
                if (room.description.isNotBlank()) {
                    SeeMore {
                        maxLines = 2
                        lineHeight = 18.0
                        backgroundColor = palette.color
                        linkCss = ClassName {
                            color = Color("inherit")
                        }
                        TextWithLinks {
                            text = room.description
                        }
                    }
                }
                RoomAttributionInfo { this.room = room }
                ClientExtension.forEach { it.roomInfoExtra(room, this) }
            }
        } else {
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
                RoomAttributionInfo { this.room = room }
                ClientExtension.forEach { it.roomInfoExtra(room, this) }
            }
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
            
            // Captured before the memo: use()-based accessors inside a useMemo body
            // would not invalidate it on context change.
            val myUser = useMyUser()
            // The room's question slice: censored questions (own + effective),
            // entities only — counts travel on room/<id>/questions/counts.
            val roomQuestionsWS = useSubscription(Topics.roomQuestions(room.ref))
            val questionMap = (roomQuestionsWS as? WSData)?.data ?: emptyMap()
            // Keyed on the room object, not just its id: the route set depends on
            // room fields (presentation, scoring, isGroup, membership-derived
            // permissions). Keying on the id alone left the list stale when e.g. a
            // presentation was created — the tab appeared, but its route did not,
            // so the tab rendered empty until a reload.
            val routes = useMemo(room, myUser, questionMap) {
                buildList {
                    if (room.isGroup) {
                        // Group rooms land on their child-room list (the Questions slot)
                        add(jso<RouteObject> {
                            index = true
                            element = GroupChildRoomList.create {
                                this.group = room
                            }
                        })
                    }
                    if (room.hasPermission(myUser, RoomPermission.VIEW_QUESTIONS)) {
                        add(jso<RouteObject> {
                            index = true
                            element = QuestionList.create {
                                // Use effective questions for display if provided (e.g. aggregated from child rooms),
                                // otherwise use the room's own questions.
                                val effQuestions = room.effectiveQuestions
                                    ?.filter { it.visibleInList }
                                    ?.mapNotNull { questionMap[it.question.id] }
                                questions = effQuestions ?: room.questions.mapNotNull { questionMap[it.id] }
                                showHiddenQuestions = room.hasPermission(myUser, RoomPermission.VIEW_HIDDEN_QUESTIONS)
                            }
                        })
                    }
                    if (room.hasPermission(myUser, RoomPermission.VIEW_ROOM_COMMENTS)) {
                        add(jso<RouteObject> {
                            path = "discussion"
                            element = RoomComments.create()
                        })
                    }
                    if (room.hasPermission(myUser, RoomPermission.MANAGE_MEMBERS)) {
                        add(jso<RouteObject> {
                            path = "members"
                            element = RoomMembers.create()
                        })
                    }
                    if (room.hasPermission(myUser, RoomPermission.VIEW_QUESTIONS)) {
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
                    if (room.hasPermission(myUser, RoomPermission.MANAGE_QUESTIONS)) {
                        add(jso<RouteObject> {
                            path = "manage_questions"
                            element = QuestionManagement.create {
                                questions = room.questions.mapNotNull { questionMap[it.id] }
                                this.room = room
                            }
                        })
                    }
                    
                    if (room.hasPermission(myUser, RoomPermission.MANAGE_QUESTIONS) && room.presentation != null) {
                        add(jso<RouteObject> {
                            path = "presentation"
                            element = PresentationTab.create()
                        })
                    }

                    // Add extension routes
                    ClientExtension.enabled.forEach { extension ->
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
