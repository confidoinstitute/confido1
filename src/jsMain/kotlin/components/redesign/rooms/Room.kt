package components.redesign.rooms

import components.*
import components.redesign.questions.*
import components.redesign.rooms.import.ImportWizard
import js.objects.unsafeJso as jso
import react.*
import react.router.*
import rooms.*
import web.url.URLSearchParams
import web.window.window

val RoomContext = createContext<Room>()
val Room = FC<Props> {
    val (appState, stale) = use(AppStateContext)!!
    val roomId = useParams()["roomID"] ?: return@FC
    val room = appState.rooms[roomId] ?: return@FC
    val loc = useLocation()

    useEffectWithCleanup(roomId) {
        window.scrollTo(0.0, 0.0)
        onCleanup {
            window.scrollTo(0.0, 0.0)
        }
    }

    val routes = useMemo(roomId, appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS)) {
        buildList {
            add(jso<RouteObject> {
                path = "*"
                element = RoomLayout.create {
                    key = roomId
                }
            })
            add(jso<RouteObject> {
                path = "edit"
                element = RoomLayout.create {
                    key = roomId
                    openEdit = true
                    openSchedule = URLSearchParams(loc.search).get("schedule") == "1"
                }
            })
            if (appState.hasPermission(room, RoomPermission.ADD_QUESTION)) {
                add(jso<RouteObject> {
                    path = "import"
                    element = ImportWizard.create()
                })
            }
            if (appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS)) {
                add(jso<RouteObject> {
                    path = "questions/:questionID"
                    children = arrayOf(
                        jso<RouteObject> {
                            index = true
                            element = Question.create()
                        },
                        jso<RouteObject> {
                            path = "resolve"
                            element = Question.create {
                                openResolve = true
                            }
                        }
                    )
                })
            }
        }.toTypedArray()
    }

    RoomContext.Provider {
        value = room
        +useRoutes(routes)
    }
}
