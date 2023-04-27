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
import emotion.react.*
import ext.showmoretext.ShowMoreText
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
import utils.questionUrl
import utils.roomUrl
import utils.runCoroutine
import web.prompts.confirm
import web.timers.*

val RoomHeaderButton = Button.withStyle {
        margin = 0.px
        padding = 7.px
        fontSize = 13.px
        lineHeight = 16.px
        fontWeight = integer(600)
}

val RoomHeader = FC<PropsWithChildren> { props ->
    val layoutMode = useContext(LayoutModeContext)
    Stack {
        direction = FlexDirection.row
        css {
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
            css {
                width = layoutMode.contentWidth
                justifyContent = JustifyContent.spaceBetween
            }
            +props.children
        }
    }
}

val RoomLayout = FC<Props> {
    val (appState, _) = useContext(AppStateContext)
    val room = useContext(RoomContext)
    val navigate = useNavigate()

    val size = useElementSize<HTMLDivElement>()
    val tabRef = useRef<HTMLDivElement>()
    val palette = room.color.palette

    var scrollY by useState(0.0)
    val cutoff = 60.0 + size.height - 15.0

    var dialogOpen by useState(false)
    var editOpen by useState(false)
    var exportOpen by useState(false)

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
        onClose = { editOpen = false }
    }
    CsvExportDialog {
        open = exportOpen
        onClose = { exportOpen = false }
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
                    // TODO: Check for confirmation properly
                    if (confirm("Are you sure you want to delete the room? This action is irreversible. Deleting will also result in loss of all questions, predictions, and discussions made in this room.")) {
                        delete()
                    }
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
                background = linearGradient(stop(palette.color, 0.pct), stop(palette.color.addAlpha("0"), 100.pct))
                zIndex = integer(1)
            }
        }
        div {
            ref = size.ref
            css {
                marginTop = (60+44).px
                width = 100.pct
                fontSize = 26.px
                lineHeight = 31.px
                fontWeight = integer(700)
                fontFamily = serif
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
            ShowMoreText {
                lines = 5
                more = ReactNode("See more")
                less = ReactNode("")
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
                        questions = room.questions.mapNotNull { it.deref() }
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
        }
    }
}