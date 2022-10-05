package components.rooms

import components.*
import components.questions.QuestionList
import react.FC
import react.Props
import react.create
import react.router.Route
import react.router.Routes
import react.router.useParams
import react.useContext


val Room = FC<Props> {
    val clientAppState = useContext(AppStateContext)
    val state = clientAppState.state
    val roomId = useParams()["roomID"] ?: return@FC
    val room = state.getRoom(roomId) ?: return@FC

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
                questions = room.questions
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
