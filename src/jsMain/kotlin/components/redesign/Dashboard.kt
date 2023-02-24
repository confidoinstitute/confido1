package components.redesign

import browser.window
import components.AppStateContext
import components.LoginContext
import components.redesign.basic.Backdrop
import components.redesign.basic.DialogMenu
import components.redesign.basic.DialogMenuItem
import components.redesign.basic.Stack
import components.redesign.forms.IconButton
import components.redesign.layout.DemoWelcomeBox
import components.redesign.questions.QuestionSticker
import components.redesign.rooms.RoomList
import components.showError
import csstype.*
import emotion.react.css
import hooks.useDocumentTitle
import icons.Feedback
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.main
import react.router.useNavigate
import tools.confido.refs.deref
import tools.confido.state.appConfig
import utils.runCoroutine

internal fun ChildrenBuilder.title(text: String) = div {
    css {
        padding = Padding(24.px, 20.px, 0.px)
        fontFamily = FontFamily.sansSerif
        fontSize = 14.px
        lineHeight = 17.px
        color = Color("#999999")
    }
    +text
}

val Dashboard = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)

    useDocumentTitle("Confido")

    var dialogOpen by useState(false)
    DashboardDialog {
        open = dialogOpen
        onClose = {dialogOpen = false}
    }

    header {
        css {
            width = 100.pct
            height = 60.px
            position = Position.fixed
            top = 0.px
            display = Display.flex
            alignItems = AlignItems.center
            gap = 6.px
            padding = Padding(0.px, 20.px)
            flexShrink = number(0.0)
            backgroundColor = Color("#F2F2F2")
        }
        appState.session.user?.let { user ->
            div {
                css {
                    width = 36.px
                    height = 36.px
                    borderRadius = 8.px
                    backgroundColor = utils.stringToColor(user.id)
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
                        fontFamily = FontFamily.sansSerif
                        fontWeight = FontWeight.bold
                        fontSize = 15.px
                        lineHeight = 18.px
                        color = Color("#222222")
                    }
                    +(user.nick ?: "Anonymous user")
                }
                div {
                    css {
                        fontFamily = FontFamily.sansSerif
                        fontSize = 15.px
                        lineHeight = 18.px
                        color = Color("#777777")
                    }
                    +(user.email ?: "")
                }
            }
            IconButton {
                NavMenuIcon {}
                onClick = {
                    dialogOpen = true
                }
            }
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
        }

        title("Recently opened")

        Stack {
            direction = FlexDirection.row
            css {
                gap = 15.px
                padding = Padding(5.px, 20.px)
                overflow = Auto.auto
                flexShrink = number(0.0)
            }

            appState.rooms.values.map { room ->
                room.questions.map { qRef ->
                    qRef.deref()?.let { question ->
                        if (question.open)
                        QuestionSticker {
                            this.room = room
                            this.question = question
                        }
                    }
                }
            }
        }

        title("Rooms")

        RoomList {
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

    DialogMenu {
        open = props.open
        onClose = { props.onClose?.invoke() }

        DialogMenuItem {
            text = "Log out"
            icon = LogoutIcon
            onClick = {
                runCoroutine {
                    Client.send("/logout", onError = { showError?.invoke(it) }) {
                        navigate("/")
                        loginState.logout()
                    }
                }
            }
        }
    }
}