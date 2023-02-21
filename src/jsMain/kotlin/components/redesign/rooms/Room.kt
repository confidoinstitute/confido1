package components.redesign.rooms

import components.AppStateContext
import components.redesign.questions.Question
import components.rooms.RoomContext
import kotlinx.js.get
import react.*
import react.router.Route
import react.router.Routes
import react.router.useParams
import rooms.RoomPermission

val Room = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val roomId = useParams()["roomID"] ?: return@FC
    val room = appState.rooms[roomId] ?: return@FC

    RoomContext.Provider {
        value = room

        Routes {
            Route {
                path = "/*"
                index = true
                this.element = RoomLayout.create()
            }
            if (appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS))
                Route {
                    path = "/question/:questionID"
                    index = true
                    this.element = Question.create()
                }
        }
    }
}
