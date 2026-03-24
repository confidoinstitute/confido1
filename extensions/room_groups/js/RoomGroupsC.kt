package extensions.room_groups

import Client
import components.AppStateContext
import components.redesign.basic.Stack
import components.redesign.basic.sansSerif
import components.redesign.basic.showError
import components.redesign.forms.FormSection
import components.redesign.forms.Select
import components.redesign.layout.LayoutMode
import components.redesign.rooms.*
import web.cssom.*
import emotion.react.css
import io.ktor.client.call.*
import js.objects.unsafeJso as jso
import payloads.requests.BaseRoomInformation
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.option
import react.router.RouteObject
import rooms.*
import tools.confido.extensions.*
import tools.confido.refs.Ref
import tools.confido.state.SentState
import tools.confido.state.globalState
import kotlinx.coroutines.delay
import utils.runCoroutine

object RoomGroupsCE : ClientExtension, RoomGroupsExtension() {

    // --- Sidebar: grouped room entries ---

    override fun renderSidebarRoomEntries(
        rooms: Map<String, Room>, small: Boolean, cb: ChildrenBuilder
    ): Boolean {
        // If no groups exist, fall back to default rendering
        if (rooms.values.none { it.isGroup }) return false

        val childIds = rooms.values.mapNotNull { it.parentRoomId }.toSet()

        // Top-level: groups and rooms without a parent
        val topLevel = rooms.values.filter { it.parentRoomId == null }

        cb.apply {
            topLevel.forEach { room ->
                if (room.isGroup) {
                    // Group header
                    RoomListEntries {
                        this.rooms = listOf(room)
                        this.small = small
                    }
                    // Child rooms, indented
                    val children = rooms.values.filter { it.parentRoomId == room.id }
                    if (children.isNotEmpty()) {
                        div {
                            css {
                                paddingLeft = 20.px
                            }
                            RoomListEntries {
                                this.rooms = children.toList()
                                this.small = small
                            }
                        }
                    }
                } else if (room.id !in childIds) {
                    // Ungrouped room (not a child of any group)
                    RoomListEntries {
                        this.rooms = listOf(room)
                        this.small = small
                    }
                }
            }
        }
        return true
    }

    // --- Room tabs: "Rooms" tab for group rooms ---

    override fun roomTabsExtra(
        room: Room, appState: SentState, layoutMode: LayoutMode
    ): List<Pair<String, String>> {
        if (!room.isGroup) return emptyList()
        return listOf("rooms" to "Rooms")
    }

    override fun roomRoutesExtra(room: Room): Array<RouteObject> {
        if (!room.isGroup) return emptyArray()
        return arrayOf(
            jso {
                path = "rooms"
                element = GroupChildRoomList.create { this.group = room }
            }
        )
    }

    // --- Room settings: group selector ---

    override fun editRoomSettingsExtra(
        room: Room?,
        onExtensionDataChange: ((ExtensionData) -> ExtensionData) -> Unit,
        cb: ChildrenBuilder,
    ) {
        if (room == null) return  // skip during room creation for now

        // Don't show group settings for group rooms themselves
        if (room.isGroup) return

        val allRooms = globalState.rooms
        val availableGroups = allRooms.values.filter { it.isGroup && it.id != room.id }

        // Track selected group locally (initialized from room's current parent)
        var selectedGroupId by useState(room.parentRoomId ?: "")

        cb.apply {
            FormSection {
                title = "Room group"
                div {
                    css {
                        padding = Padding(10.px, 15.px)
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        gap = 8.px
                    }
                    div {
                        css {
                            fontFamily = sansSerif
                            fontSize = 14.px
                            color = Color("#555555")
                        }
                        +"Assign this room to a group for shared members and aggregated scores."
                    }
                    Select {
                        value = selectedGroupId
                        onChange = { event ->
                            val value = event.target.value
                            if (value == "::new") {
                                // Create new group via standard room creation API
                                // Small delay lets the browser close the dropdown before showing the prompt
                                runCoroutine {
                                    delay(100)
                                    val name = web.window.window.asDynamic().prompt("Group name:") as? String
                                    if (!name.isNullOrBlank()) {
                                        val groupInfo = BaseRoomInformation(
                                            name = name,
                                            description = "",
                                            color = RoomColor.BLUE,
                                            icon = null,
                                            extensionData = ExtensionData(RoomEDT).with(IsGroupKey, true),
                                        )
                                        Client.sendData("/rooms/add", groupInfo, onError = { showError(it) }) {
                                            val newGroupId: String = body()
                                            selectedGroupId = newGroupId
                                            onExtensionDataChange { ed ->
                                                ed.with(ParentRoomKey, Ref<Room>(newGroupId))
                                            }
                                        }
                                    }
                                }
                                // If user cancelled, don't change selection
                            } else {
                                selectedGroupId = value
                                onExtensionDataChange { ed ->
                                    if (value.isEmpty()) {
                                        ed.without(ParentRoomKey)
                                    } else {
                                        ed.with(ParentRoomKey, Ref<Room>(value))
                                    }
                                }
                            }
                        }
                        option {
                            value = ""
                            +"(No group)"
                        }
                        availableGroups.forEach { group ->
                            option {
                                value = group.id
                                +group.name
                            }
                        }
                        option {
                            value = "::new"
                            +"Create new group…"
                        }
                    }
                }
            }
        }
    }
}

// --- Components ---

/** Page showing child rooms of a group, used as the "Rooms" tab content. */
private external interface GroupChildRoomListProps : Props {
    var group: Room
}

private val GroupChildRoomList = FC<GroupChildRoomListProps> { props ->
    val (appState, stale) = use(AppStateContext)!!

    val children = appState.rooms.values.filter { it.parentRoomId == props.group.id }

    Stack {
        css {
            padding = 20.px
            gap = 8.px
        }
        if (children.isEmpty()) {
            div {
                css {
                    fontFamily = sansSerif
                    fontSize = 14.px
                    color = Color("#888888")
                    padding = 20.px
                }
                +"No rooms in this group yet. Add rooms to this group from each room's settings."
            }
        } else {
            RoomListEntries {
                rooms = children.toList()
                small = false
            }
        }
    }
}
