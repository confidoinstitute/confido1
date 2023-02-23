package components.redesign.rooms

import browser.window
import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.*
import components.rooms.RoomContext
import components.rooms.RoomMembers
import csstype.*
import csstype.FlexDirection.Companion.row
import csstype.FontFamily.Companion.sansSerif
import dom.ScrollBehavior
import dom.html.HTMLDivElement
import emotion.react.*
import hooks.*
import kotlinx.js.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.main
import react.router.Route
import react.router.Routes
import rooms.RoomPermission
import tools.confido.refs.deref
import web.timers.requestAnimationFrame

fun PropertiesBuilder.bigQuestionTitle() {
    fontSize = 26.px
    lineHeight = 31.px
    fontWeight = FontWeight.bold
}

fun PropertiesBuilder.navQuestionTitle() {
    fontSize = 17.px
    lineHeight = 21.px
    fontWeight = FontWeight.bold
    overflow = Overflow.hidden
    whiteSpace = WhiteSpace.nowrap
    textOverflow = TextOverflow.ellipsis
}

val RoomHeaderButton = FC<ButtonProps> { props ->
    Button {
        css {
            margin = 0.px
            padding = 7.px
            fontSize = 13.px
            lineHeight = 16.px
            fontWeight = integer(600)
        }
        +props
    }
}

val RoomHeader = FC<PropsWithChildren> { props ->
    Stack {
        direction = row
        css {
            backgroundColor = Color("#FFFFFF")
            borderBottom = Border(0.5.px, LineStyle.solid, Color("#CCCCCC"))
            justifyContent = JustifyContent.spaceBetween
            padding = Padding(15.px, 14.px, 15.px, 15.px)
            position = Position.sticky
            top = 76.px
            zIndex = integer(20)
        }

        +props.children
    }
}

val RoomLayout = FC<Props> {
    val (appState, _) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    val size = useElementSize<HTMLDivElement>()
    val tabRef = useRef<HTMLDivElement>()
    val palette = RoomPalette.red

    var scrollY by useState(0.0)
    val cutoff = 60.0 + size.height - 15.0

    var dialogOpen by useState(false)

    useDocumentTitle("${room.name} - Confido")

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

    Dialog {
        open = dialogOpen
        onClose = {dialogOpen = false}
        title = "Do something with room"
        action = "Do it"

        Form {
            FormSection {
                title = "SOMETHING"
                FormField {
                    title = "Name"
                    required = true
                    comment = "Name of something, I don't know."
                    TextInput {}
                }
            }

            Button {
                +"DO IT"
            }
        }
    }

    RoomNavbar {
        navigateBack = "/"
        div {
            css {
                position = Position.relative
                flexShrink = number(1.0)
                color = palette.text.color
                padding = 12.px
                navQuestionTitle()
                if (scrollY <= cutoff)
                    visibility = Visibility.hidden
            }
            +room.name
        }
    }
    Stack {
        component = header
        css {
            backgroundColor = palette.color
            flexDirection = FlexDirection.column
            height = 100.pct
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
                fontFamily = sansSerif
                marginTop = (60+44).px
                width = 100.pct
                textAlign = TextAlign.center
                color = palette.text.color
                bigQuestionTitle()
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
            }
            +room.description
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