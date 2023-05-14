package components.redesign.rooms

import browser.window
import components.*
import components.redesign.questions.*
import components.rooms.*
import kotlinx.js.*
import react.*
import react.router.*
import rooms.*

val Room = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val roomId = useParams()["roomID"] ?: return@FC
    val room = appState.rooms[roomId] ?: return@FC

    useEffect(roomId) {
        window.scrollTo(0, 0)
        cleanup {
            window.scrollTo(0, 0)
        }
    }

    RoomContext.Provider {
        value = room

        Routes {
            Route {
                path = "*"
                index = true
                this.element = RoomLayout.create {
                    key = roomId
                }
            }
            if (appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS))
                Route {
                    path = "questions/:questionID"
                    this.element = Question.create()
                }
        }
    }
}
