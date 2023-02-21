package components.redesign

import components.AppStateContext
import components.redesign.basic.Stack
import components.redesign.forms.IconButton
import components.redesign.questions.QuestionSticker
import components.redesign.rooms.RoomList
import csstype.*
import emotion.react.css
import icons.Feedback
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.main
import react.useContext
import tools.confido.refs.deref

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

    Stack {
        css {
            width = 100.vw
            height = 100.vh
            backgroundColor = Color("#F2F2F2")
        }
        header {
            css {
                height = 60.px
                display = Display.flex
                alignItems = AlignItems.center
                gap = 6.px
                padding = Padding(0.px, 20.px)
                flexShrink = number(0.0)
            }
            appState.session.user?.let {user ->
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
                    Feedback {}
                }
                IconButton {
                    NavMenuIcon {}
                }
            }
        }

        main {
            css {
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
                    padding = Padding(0.px, 20.px)
                    overflow = Auto.auto
                    flexShrink = number(0.0)
                }

                appState.rooms.values.map { room ->
                    room.questions.map { qRef ->
                        qRef.deref()?.let { question ->
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
                canCreate = true
            }
        }
    }
}
