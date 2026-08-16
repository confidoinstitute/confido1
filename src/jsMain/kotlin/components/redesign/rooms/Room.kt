package components.redesign.rooms

import components.*
import components.redesign.questions.*
import components.redesign.rooms.import.ImportWizard
import hooks.useSubscription
import js.objects.unsafeJso as jso
import payloads.responses.WSData
import react.*
import react.router.*
import rooms.*
import web.url.URLSearchParams
import web.window.window
import tools.confido.state.Topics
import tools.confido.refs.Ref
import components.useRooms

val RoomContext = createContext<Room>()
val Room = FC<Props> {
    val roomId = useParams()["roomID"] ?: return@FC
    // Component-scoped subscription to the full room (member lists, invite links,
    // presentation state); the rooms directory only carries directory-level rooms.
    // Until the detail payload arrives, fall back to the directory entry — the page
    // renders with configuration data and fills in member detail when it lands.
    val roomWS = useSubscription(Topics.room(Ref(roomId)))
    val roomDir = useRooms()
    // On a cold load both may still be in flight; the page body lives in an inner
    // component that only mounts once the room is in hand, so its hook order can
    // never differ between a loading and a loaded render (React #310).
    val room = (roomWS as? WSData)?.data ?: roomDir[roomId] ?: return@FC
    RoomInner {
        key = roomId
        this.room = room
    }
}

private external interface RoomInnerProps : Props {
    var room: Room
}

private val RoomInner = FC<RoomInnerProps> { props ->
    val room = props.room
    val roomId = room.id
    val loc = useLocation()

    useEffectWithCleanup(roomId) {
        window.scrollTo(0.0, 0.0)
        onCleanup {
            window.scrollTo(0.0, 0.0)
        }
    }

    // Captured before the memo: use()-based accessors inside a useMemo body would
    // not invalidate it on context change.
    val canViewQuestions = usePermission(room, RoomPermission.VIEW_QUESTIONS)
    val canAddQuestion = usePermission(room, RoomPermission.ADD_QUESTION)
    val routes = useMemo(roomId, canViewQuestions, canAddQuestion) {
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
            if (canAddQuestion) {
                add(jso<RouteObject> {
                    path = "import"
                    element = ImportWizard.create()
                })
            }
            if (canViewQuestions) {
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
