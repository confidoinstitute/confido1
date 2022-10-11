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
import tools.confido.refs.deref
import tools.confido.refs.eqid
import utils.stringToColor
import utils.themed

val RoomContext = createContext<Room>()

val Room = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val currentUser = appState.session.user ?: return@FC
    val roomId = useParams()["roomID"] ?: return@FC
    val room = appState.rooms[roomId] ?: return@FC

    var editMode by useState(false)


    RoomContext.Provider {
        value = room

        // TODO properly make edit mode
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

                    if (appState.hasPermission(room, RoomPermission.ROOM_OWNER))
                    IconButton {
                        onClick = {editMode = true}
                        EditIcon { }
                    }
                }

                val seesUsers = appState.isFullUser
                AvatarGroup {
                    max = 4
                    console.log(room.members)
                    room.members.sortedBy {
                        // Force yourself to be the first shown member
                        if (it.user eqid currentUser) null else it.user.id
                    }.map {membership ->
                        if ((seesUsers || membership.user eqid currentUser) && membership.user.deref() != null )
                            UserAvatar {
                                key = membership.user.id
                                user = membership.user.deref()!!
                            }
                        else
                            Avatar {}
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
            if (appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS))
            Route {
                index = true
                this.element = QuestionList.create {
                    questions = room.questions.mapNotNull { it.deref() }
                    // TODO: This should be fully handled by the server.
                    showHiddenQuestions = appState.hasPermission(room, RoomPermission.VIEW_HIDDEN_QUESTIONS)
                    allowEditingQuestions = appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS)
                }
            }
            // TODO view comments permission
            if (true)
            Route {
                path = "discussion"
                this.element = RoomComments.create {}
            }
            if (appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS))
            Route {
                path = "manage_questions"
                this.element = EditQuestions.create {
                    questions = room.questions.mapNotNull { it.deref() }
                    allowEditingQuestions = appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS)
                }
            }
            if (appState.hasPermission(room, RoomPermission.MANAGE_MEMBERS))
            Route {
                path = "members"
                this.element = RoomMembers.create()
            }
        }
    }
}
