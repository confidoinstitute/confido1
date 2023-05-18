package components.redesign

import Client
import components.*
import components.redesign.basic.*
import components.redesign.forms.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.*
import components.redesign.rooms.*
import csstype.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.span
import react.router.useNavigate
import tools.confido.question.QuestionState
import tools.confido.refs.deref
import tools.confido.state.appConfig
import users.User
import utils.runCoroutine

internal fun ChildrenBuilder.title(text: String, sidePad: Length) = div {
    css {
        padding = Padding(24.px, sidePad, 0.px)
        fontFamily = sansSerif
        fontSize = 14.px
        lineHeight = 17.px
        fontWeight = integer(500)
        color = Color("#999999")
    }
    +text
}

external interface WorkspaceFrameProps : PropsWithClassName {
    var user: User
}

val AccountFrame = FC<WorkspaceFrameProps> { props ->
    val layoutMode = useContext(LayoutModeContext)
    Stack {
        direction = FlexDirection.row
        css(props.className) {
            alignItems = AlignItems.center
            gap = 9.px
        }
        div {
            css {
                width = 36.px
                height = 36.px
                borderRadius = 50.pct
                backgroundColor = utils.stringToColor(props.user.id)
                flexShrink = number(0.0)
            }
        }
        Stack {
            css {
                flexGrow = number(1.0)
                flexShrink = number(1.0)
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
                whiteSpace = WhiteSpace.nowrap
            }
            div {
                css {
                    fontFamily = sansSerif
                    fontWeight = integer(600)
                    fontSize = 15.px
                    lineHeight = 18.px
                    color = Color("#222222")
                }
                +(props.user.nick ?: "Anonymous user")
            }
            div {
                css {
                    fontFamily = sansSerif
                    if (layoutMode == LayoutMode.PHONE) {
                        fontSize = 15.px
                        lineHeight = 18.px
                    } else {
                        fontSize = 10.px
                        lineHeight = 12.px
                    }
                    color = Color("#777777")
                }
                +(props.user.email ?: "")
            }
        }
    }
}

external interface DashboardHeaderProps : Props {
    var onAccountDialogOpen: (() -> Unit)?
}

external interface DemoPillProps : Props {
    var small: Boolean?
}

val DemoPill = FC<DemoPillProps> { props ->
    val small = props.small ?: false
    span {
        css {
            fontFamily = sansSerif
            if (small) {
                fontSize = 13.px
                lineHeight = 16.px
            } else {
                fontSize = 14.px
                lineHeight = 17.px
            }
            fontWeight = integer(600)
            borderRadius = 20.px
            backgroundColor = RoomPalette.red.color
            color = RoomPalette.red.text.color
            padding = Padding(3.px, 7.px)
            alignSelf = AlignSelf.start
        }
        +"DEMO"
    }
}

val DesktopHeader = FC<DashboardHeaderProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val layoutMode = useContext(LayoutModeContext)
    header {
        css {
            display = Display.flex
            justifyContent = JustifyContent.center
            flexShrink = number(0.0)
            height = 60.px
            width = 100.pct
            padding = Padding(20.px, 0.px, 60.px)
            backgroundColor = Color("#F2F2F2")
        }

        div {
            css {
                height = 60.px
                display = Display.flex
                alignItems = AlignItems.center
                justifyContent = JustifyContent.spaceBetween
                padding = Padding(0.px, layoutMode.contentSidePad)
                width = layoutMode.contentWidth
            }

            Stack {
                direction = FlexDirection.row
                img {
                    css {
                        height = 60.px
                    }
                    src = "/static/sidebar_logo.svg"
                }
                if (appConfig.demoMode) {
                    DemoPill {}
                }
            }

            appState.session.user?.let { user ->
                ButtonBase {
                    css {
                        borderRadius = 30.px
                        ".ripple" {
                            backgroundColor = Color("#000000")
                        }
                    }
                    Stack {
                        direction = FlexDirection.row
                        css {
                            alignItems = AlignItems.center
                            padding = Padding(4.px, 13.px, 4.px, 4.px)
                            gap = 15.px
                            hover {
                                backgroundColor = Color("#FFFFFF")
                            }
                        }
                        AccountFrame {
                            css {
                                flexGrow = number(1.0)
                                flexShrink = number(1.0)
                            }
                            this.user = user
                        }
                        NavMenuIcon {}
                    }
                    onClick = {
                        props.onAccountDialogOpen?.invoke()
                    }
                }
            }
        }
    }
}

val PhoneHeader = FC<DashboardHeaderProps> { props ->
    val (appState, stale) = useContext(AppStateContext)
    val layoutMode = useContext(LayoutModeContext)
    header {
        css {
            display = Display.flex
            justifyContent = JustifyContent.center
            flexShrink = number(0.0)
            height = 60.px
            width = 100.pct
            top = 0.px
            position = Position.fixed
            backgroundColor = Color("#F2F2F2")
        }

        div {
            css {
                height = 60.px
                display = Display.flex
                alignItems = AlignItems.center
                padding = Padding(0.px, layoutMode.contentSidePad)
                width = layoutMode.contentWidth
            }
            appState.session.user?.let { user ->
                AccountFrame {
                    css {
                        flexGrow = number(1.0)
                        flexShrink = number(1.0)
                    }
                    this.user = user
                }
                IconButton {
                    NavMenuIcon {}
                    onClick = {props.onAccountDialogOpen?.invoke() }
                }
            }
        }
    }
}

val Dashboard = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val layoutMode = useContext(LayoutModeContext)

    var dialogOpen by useState(false)
    DashboardDialog {
        open = dialogOpen
        onClose = { dialogOpen = false }
    }

    if (layoutMode >= LayoutMode.TABLET) {
        DesktopHeader {
            onAccountDialogOpen = { dialogOpen = true }
        }
    }

    if (layoutMode == LayoutMode.PHONE) {
        PhoneHeader {
            onAccountDialogOpen = { dialogOpen = true }
        }
    }

    val recentlyOpenedQuestions = useMemo(appState) {
        appState.rooms.values.flatMap {room ->
            room.questions.mapNotNull { it.deref() }.filter { it.open }.map {room to it}
        }.sortedByDescending { (_, q) ->
            q.stateHistory.filter { it.newState == QuestionState.OPEN }.maxOfOrNull { it.at }
        }
    }

    main {
        css {
            marginTop = 60.px
            flexGrow = number(1.0)
            display = Display.flex
            flexDirection = FlexDirection.column
            overflow = Auto.auto
            gap = 12.px
            width = layoutMode.contentWidth
            overflow = Overflow.visible
            alignSelf = AlignSelf.center
        }

        title("Recently opened questions", layoutMode.contentSidePad)

        Stack {
            direction = FlexDirection.row
            css {
                // On desktop layout, ensure a whole number of tiles (4) fit into the 640px
                // container
                gap = if (layoutMode >= LayoutMode.TABLET) 21.px else 15.px
                padding = Padding(5.px, layoutMode.contentSidePad)
                overflow = Auto.auto
                flexShrink = number(0.0)
            }

            recentlyOpenedQuestions.forEach { (room, question) ->
                QuestionSticker {
                    this.room = room
                    this.question = question
                }
            }
        }

        title("Rooms", layoutMode.contentSidePad)

        RoomList {
            canCreate = appState.session.user?.type?.isProper() ?: false
        }
    }
}

external interface DashboardDialogProps : Props {
    var open: Boolean
    var onClose: (() -> Unit)?
}

val DashboardDialog = FC<DashboardDialogProps> { props ->
    val loginState = useContext(LoginContext)
    val navigate = useNavigate()
    val (appState, stale) = useContext(AppStateContext)

    DialogMenu {
        open = props.open
        onClose = { props.onClose?.invoke() }

        DialogMenuNav {
            text = "User settings"
            icon = SettingsIcon
            this.navigate = "/profile"
        }

        DialogMenuItem {
            text = "Log out"
            icon = LogoutIcon
            variant = DialogMenuItemVariant.dangerous
            onClick = {
                runCoroutine {
                    Client.send("/logout", onError = { showError(it) }) {
                        navigate("/")
                        loginState.logout()
                    }
                }
            }
        }
        DialogMenuSeparator {}
        DialogMenuCommonActions {
            pageName = "Dashboard"
            onClose = props.onClose
        }
        if (appState.isAdmin()) {
            DialogMenuSeparator{}
            DialogMenuHeader { this.text = "Administration" }
            DialogMenuNav {
                text = "User management"
                this.navigate = "/admin/users"
            }
        }
    }
}