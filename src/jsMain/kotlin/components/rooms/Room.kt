package components.rooms

import components.*
import components.questions.QuestionList
import components.questions.QuestionTable
import csstype.AlignItems
import csstype.WhiteSpace
import csstype.number
import icons.EditIcon
import io.ktor.client.plugins.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import payloads.requests.BaseRoomInformation
import react.*
import react.router.Route
import react.router.Routes
import react.router.useParams
import rooms.Room
import rooms.RoomPermission
import tools.confido.refs.deref
import tools.confido.refs.eqid
import utils.postJson
import utils.themed
import kotlin.coroutines.EmptyCoroutineContext

val RoomContext = createContext<Room>()

val RoomInformation = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val room = useContext(RoomContext)

    var editMode by useState(false)
    var editPending by useState(false)
    var editError by useState(false)

    val currentUser = appState.session.user ?: return@FC

    val editRoom: ((BaseRoomInformation) -> Unit) = useMemo(room) {
        {information ->
            CoroutineScope(EmptyCoroutineContext).launch {
                editPending = true
                try {
                    Client.httpClient.postJson("/rooms/${room.id}/edit", information) {
                        expectSuccess = true
                    }
                    editMode = false
                    editError = false
                } catch (e: Throwable) {
                    editError = true
                } finally {
                    editPending = false
                }
            }
        }
    }

    if (editMode) {
        Collapse {
            this.`in` = editError
            Alert {
                severity = AlertColor.error
                +"There was a failure in editing the room details."
            }
        }
        RoomInfoForm {
            this.room = room
            this.disabled = editPending
            this.onCancel = {editMode = false}
            this.onSubmit = editRoom
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
                        disabled = stale
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
                whiteSpace = WhiteSpace.preLine
                marginBottom = themed(2)
            }
            TextWithLinks { text = room.description }
        }
    }
}

val Room = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val roomId = useParams()["roomID"] ?: return@FC
    val room = appState.rooms[roomId] ?: return@FC


    RoomContext.Provider {
        value = room

        RoomInformation {
            key = room.id
        }

        RoomNavigation {
        }
        Routes {
            if (appState.hasPermission(room, RoomPermission.VIEW_QUESTIONS))
            Route {
                index = true
                this.element = QuestionList.create {
                    questions = room.questions.mapNotNull { it.deref() }
                    showHiddenQuestions = appState.hasPermission(room, RoomPermission.VIEW_HIDDEN_QUESTIONS)
                    allowEditingQuestions = appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS)
                }
            }
            if (appState.hasPermission(room, RoomPermission.VIEW_ROOM_COMMENTS))
            Route {
                path = "discussion"
                this.element = RoomComments.create {}
            }
            if (appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS))
            Route {
                path = "manage_questions"
                this.element = QuestionTable.create {
                    this.room = room
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
