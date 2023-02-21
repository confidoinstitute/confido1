package components.redesign.rooms

import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.*
import components.redesign.questions.QuestionList
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
}

val RoomHeader = FC<PropsWithChildren> { props ->
    Stack {
        direction = row
        css {
            backgroundColor = Color("#FFFFFF")
            borderBottom = Border(0.5.px, LineStyle.solid, Color("#CCCCCC"))
            justifyContent = JustifyContent.spaceBetween
            padding = Padding(15.px, 14.px, 15.px, 15.px)
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
    val cutoff = 60 + size.height / 2

    var dialogOpen by useState(false)

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

    Stack {
        css {
            flexDirection = FlexDirection.column
            position = Position.relative
            flexGrow = number(1.0)
            height = 100.pct
        }
        RoomNavbar {
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
            onScroll = { event ->
                scrollY = event.currentTarget.scrollTop
            }
            css {
                flexGrow = number(1.0)
                position = Position.relative
                overflow = Auto.auto
            }

            div {
                css {
                    backgroundColor = palette.color
                    position = Position.relative
                }
                div {
                    css {
                        top = 0.px
                        position = Position.sticky
                        width = 100.pct
                        height = 30.px
                        background = linearGradient(stop(palette.color, 0.pct), stop(rgba(0, 0, 0, 0.0), 100.pct))
                        zIndex = integer(1)
                    }
                }
                div {
                    ref = size.ref
                    css {
                        fontFamily = sansSerif
                        paddingTop = 30.px
                        width = 100.pct
                        textAlign = TextAlign.center
                        color = palette.text.color
                        bigQuestionTitle()
                        if (scrollY > cutoff)
                            visibility = Visibility.hidden
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
            }

            div {
                ref = tabRef
            }
            RoomTabs {
                css {
                    position = Position.sticky
                    top = 0.px
                    zIndex = integer(20)
                }
                onChange = {
                    tabRef.current?.apply {
                        requestAnimationFrame {
                            scrollIntoView(jso {
                                if (scrollY < offsetTop)
                                    behavior = ScrollBehavior.smooth
                            })
                        }
                    }
                }
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
                            this.element = RoomComments.create {}
                        }
                    if (appState.hasPermission(room, RoomPermission.MANAGE_MEMBERS))
                        Route {
                            path = "members"
                            this.element = RoomMembers.create()
                        }
                }
            }
        }
    }
}