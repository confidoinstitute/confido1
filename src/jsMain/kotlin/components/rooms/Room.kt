package components.rooms

import components.*
import components.questions.QuestionList
import react.*
import react.router.Route
import react.router.Routes
import react.router.useParams
import tools.confido.question.Room

val RoomContext = createContext<Room>()

val Room = FC<Props> {
    val clientAppState = useContext(AppStateContext)
    val state = clientAppState.state
    val roomId = useParams()["roomID"] ?: return@FC
    val room = state.getRoom(roomId) ?: return@FC

    RoomContext.Provider {
        value = room
        RoomNavigation {}
        Routes {
            Route {
                index = true
                this.element = QuestionList.create {
                    questions = room.questions
                }
            }
            Route {
                path = "group_predictions"

                this.element = GroupPredictions.create {
                    questions = room.questions.filter { it.predictionsVisible }
                }
            }
            Route {
                path = "edit_questions"

                this.element = EditQuestions.create {
                    questions = room.questions
                }
            }
        }
    }
}
