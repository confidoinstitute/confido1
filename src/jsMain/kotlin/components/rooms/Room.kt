package components.rooms

import components.*
import components.questions.QuestionList
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.*
import react.router.Route
import react.router.Routes
import react.router.useParams
import rooms.Room
import rooms.RoomPermission
import utils.themed

val RoomContext = createContext<Room>()

val Room = FC<Props> {
    val clientAppState = useContext(AppStateContext)
    val state = clientAppState.state
    val roomId = useParams()["roomID"] ?: return@FC
    val room = state.getRoom(roomId) ?: return@FC

    RoomContext.Provider {
        value = room
        Typography {
            variant = TypographyVariant.h2
            +room.name
        }
        Typography {
            sx {
                marginBottom = themed(2)
            }
            +room.description
        }

        RoomNavigation {}
        Routes {
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
