package components.rooms

import components.*
import components.questions.QuestionList
import csstype.AlignItems
import csstype.number
import csstype.px
import icons.EditIcon
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import react.*
import react.router.Route
import react.router.Routes
import react.router.useParams
import rooms.Room
import rooms.RoomPermission
import utils.stringToColor
import utils.themed

val RoomContext = createContext<Room>()

val Room = FC<Props> {
    val clientAppState = useContext(AppStateContext)
    val state = clientAppState.state
    val roomId = useParams()["roomID"] ?: return@FC
    val room = state.getRoom(roomId) ?: return@FC

    var editMode by useState(false)

    RoomContext.Provider {
        value = room

        if (editMode) {
            RoomInfoForm {
                this.room = room
                this.onCancel = {editMode = false}
                this.onSubmit = {editMode = false}
            }
        } else {
            Stack {
                direction = responsive(StackDirection.row)
                spacing = responsive(2)
                sx {
                    alignItems = AlignItems.start
                }
                Typography {
                    sx {
                        flexGrow = number(1.0)
                    }
                    variant = TypographyVariant.h2
                    +room.name
                }

                // TODO permissions to edit a room
                IconButton {
                    onClick = {editMode = true}
                    size = Size.small
                    EditIcon { }
                }

                AvatarGroup {
                    room.members.map {membership ->
                        val user = membership.user
                        Avatar {
                            sx {
                                backgroundColor = stringToColor(user.id)
                            }
                            alt = user.nick
                            user.nick?.let {
                                +(it[0].toString())
                            }
                        }
                    }
                }
            }
            Typography {
                sx {
                    marginBottom = themed(2)
                }
                +room.description
            }
        }

        RoomNavigation {
            key = "room_navigation"
        }
        Routes {
            key = "room_routes"
            Route {
                index = true
                this.element = QuestionList.create {
                    questions = room.questions
                    // TODO: This should be fully handled by the server.
                    showHiddenQuestions = state.hasPermission(room, RoomPermission.VIEW_HIDDEN_QUESTIONS)
                    allowEditingQuestions = state.hasPermission(room, RoomPermission.MANAGE_QUESTIONS)
                }
            }
            Route {
                path = "group_predictions"

                this.element = GroupPredictions.create {
                    // TODO: Apply permissions
                    questions = room.questions.filter { it.predictionsVisible }
                }
            }
            Route {
                path = "edit_questions"
                this.element = EditQuestions.create {
                    questions = room.questions
                    allowEditingQuestions = state.hasPermission(room, RoomPermission.MANAGE_QUESTIONS)
                }
            }
            Route {
                path = "invites"
                this.element = NewInvite.create()
            }
        }
    }
}
