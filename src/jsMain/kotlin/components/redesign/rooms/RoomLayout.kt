package components.redesign.rooms

import browser.window
import components.AppStateContext
import components.redesign.*
import components.redesign.basic.*
import components.redesign.forms.Button
import components.redesign.layout.LayoutModeContext
import components.redesign.rooms.dialog.CsvExportDialog
import components.redesign.rooms.dialog.EditRoomSettingsDialog
import components.rooms.RoomContext
import components.showError
import csstype.*
import dom.ScrollBehavior
import dom.html.*
import emotion.css.ClassName
import emotion.react.*
import hooks.*
import io.ktor.http.*
import kotlinx.js.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.span
import react.router.*
import rooms.*
import tools.confido.refs.*
import tools.confido.utils.mapDeref
import utils.roomUrl
import utils.runCoroutine
import web.timers.*

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
    val layoutMode = useContext(LayoutModeContext)
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
    val (appState, _) = useContext(AppStateContext)
    val room = useContext(RoomContext)
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
                        behavior = ScrollBehavior.smooth
                })
            }
        }
    }

    useEffectOnce {
        val onScroll = { event: dynamic ->
            scrollY = window.scrollY
        }

        window.addEventListener("scroll", onScroll)
        cleanup {
            window.removeEventListener("scroll", onScroll)
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
                color = palette.text.color
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
                color = palette.text.color
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
    main {
        css {
            flexGrow = number(1.0)
            display = Display.flex
            flexDirection = FlexDirection.column
        }
        Routes {
            if (appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS))
                Route {
                    index = true
                    this.element = QuestionList.create {
                        questions = room.questions.mapDeref()
                        showHiddenQuestions = appState.hasPermission(room, RoomPermission.VIEW_HIDDEN_QUESTIONS)
                        allowEditingQuestions = appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS)
                    }
                }
            if (appState.hasPermission(room, RoomPermission.VIEW_ROOM_COMMENTS))
                Route {
                    path = "discussion"
                    this.element = RoomComments.create()
                }
            if (appState.hasPermission(room, RoomPermission.MANAGE_MEMBERS))
                Route {
                    path = "members"
                    this.element = RoomMembers.create()
                }
            if (appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS))
                Route {
                    path = "calibration"
                    this.element = RoomCalibration.create {
                        this.room = room
                    }
                }
            if (appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS))
                Route {
                    path = "manage_questions"
                    this.element = QuestionManagement.create {
                        questions = room.questions.mapDeref()
                        this.room = room
                    }
                }
        }
    }
}