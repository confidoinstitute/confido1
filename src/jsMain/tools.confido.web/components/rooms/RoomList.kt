package tools.confido.web.components.rooms

import tools.confido.web.components.*
import tools.confido.web.components.basic.*
import tools.confido.web.components.layout.LayoutMode
import tools.confido.web.components.layout.LayoutModeContext
import web.cssom.*
import emotion.react.*
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.svg.*
import react.dom.svg.ReactSVG.svg
import react.router.*
import react.router.dom.*
import tools.confido.rooms.Room
import tools.confido.rooms.RoomPermission
import tools.confido.extensions.ClientExtension
import tools.confido.web.utils.ExtendableLink
import tools.confido.web.utils.ExtendableLinkProps
import react.dom.html.ReactHTML.button

external interface RoomListProps : Props {
    var small: Boolean
    var canCreate: Boolean
}

external interface RoomLinkProps : ExtendableLinkProps {
    var small: Boolean
    var highlighted: Boolean
}

val RoomLink = ExtendableLink.withRipple().withStyleLM<RoomLinkProps>("small") { props, layoutMode ->
    display = Display.flex
    flexDirection = FlexDirection.row
    gap = 12.px
    textDecoration = None.none
    fontFamily = sansSerif
    alignItems = AlignItems.center
    if (props.small) {
        fontSize = 15.px
        lineHeight = 17.px
    } else {
        fontSize = 18.px
        lineHeight = 20.px
    }
    color = Color("#222222")
    padding = Padding(5.px, if (layoutMode == LayoutMode.PHONE) 20.px else 5.px)
    margin = if (layoutMode == LayoutMode.PHONE) 0.px else Margin(0.px, (-5).px)
    background = None.none
    border = None.none
    // On mobile it goes to the edge of the screen so no rounding
    borderRadius = if (layoutMode == LayoutMode.PHONE) 0.px else 5.px
    cursor = Cursor.pointer

    if (props.highlighted) {
        backgroundColor = Color("#F2F2F2")
        borderRadius = 12.px
    }

    hover {
        backgroundColor = rgb(0,0,0, 0.05)
    }
}

external interface RoomListEntriesProps : Props {
    var rooms: List<Room>
    var small: Boolean
}

/** Renders a list of rooms as RoomLink entries (icon + name). Reusable by extensions. */
val RoomListEntries = FC<RoomListEntriesProps> { props ->
    val location = useLocation()
    val iconSize = if (props.small) 36.px else 48.px

    props.rooms.map { room ->
        RoomLink {
            key = room.id
            to = room.urlPrefix
            small = props.small
            highlighted = location.pathname.startsWith(room.urlPrefix)
            div {
                css {
                    backgroundColor = room.color.palette.color
                    borderRadius = 8.px
                    width = iconSize
                    height = iconSize
                    flexShrink = number(0.0)
                    display = Display.flex
                    alignItems = AlignItems.center
                    justifyContent = JustifyContent.center
                    padding = 6.px
                }
                room.icon?.let {
                    div {
                        css {
                            mask = url("/static/icons/$it")
                            maskSize = MaskSize.contain
                            backgroundColor = room.color.palette.text.color
                            width = 100.pct
                            height = 100.pct
                        }
                    }
                }
            }
            span {
                +room.name
            }
        }
    }
}

external interface CreateEntryIconProps : Props {
    /** Square side in px (matches the room icon size next to it). */
    var size: Length?
}

/** Outlined square with a plus — the icon of "create" entries in room lists. */
val CreateEntryIcon = FC<CreateEntryIconProps> { props ->
    div {
        css {
            border = Border(1.px, LineStyle.solid, Color("#BBBBBB"))
            borderRadius = 8.px
            width = props.size ?: 48.px
            height = props.size ?: 48.px
            display = Display.flex
            alignItems = AlignItems.center
            justifyContent = JustifyContent.center
            flexShrink = number(0.0)
        }
        svg {
            width = 12.0
            height = 12.0
            strokeWidth = 2.0
            stroke = "#BBBBBB"
            ReactSVG.path {
                d = "M6,0 L6,12 M0,6 L12,6"
            }
        }
    }
}

external interface GroupRoomEntryProps : Props {
    var group: Room
    /** Child rooms of the group. (Not `children` — that is React's reserved slot.) */
    var childRooms: List<Room>
    var small: Boolean
    /** Show the hover "+" shortcut for creating a room inside the group. */
    var canCreateInGroup: Boolean
}

/** Sidebar entry for a group room: the group itself, a hover "+" shortcut for
 *  creating a room inside it, and its child rooms indented below. */
val GroupRoomEntry = FC<GroupRoomEntryProps> { props ->
    div {
        css {
            position = Position.relative
            if (props.canCreateInGroup) {
                "&:hover .create-in-group" {
                    opacity = number(1.0)
                }
            }
        }
        RoomListEntries {
            rooms = listOf(props.group)
            small = props.small
        }
        if (props.canCreateInGroup) {
            ExtendableLink {
                to = "/new_room?group=${props.group.id}"
                title = "Create a room in this group"
                // NB the marker class must go through the merging css(ClassName(...))
                // overload — `className =` and the css prop clobber each other.
                css(ClassName("create-in-group")) {
                    position = Position.absolute
                    right = 8.px
                    top = 50.pct
                    transform = translatey((-50).pct)
                    opacity = number(0.0)
                    transition = Transition(ident("opacity"), 0.15.s, 0.s)
                    display = Display.flex
                    alignItems = AlignItems.center
                    justifyContent = JustifyContent.center
                    width = 24.px
                    height = 24.px
                    borderRadius = 6.px
                    hover {
                        backgroundColor = rgb(0, 0, 0, 0.07)
                    }
                }
                svg {
                    width = 12.0
                    height = 12.0
                    strokeWidth = 2.0
                    stroke = "#666666"
                    ReactSVG.path {
                        d = "M6,0 L6,12 M0,6 L12,6"
                    }
                }
            }
        }
    }
    if (props.childRooms.isNotEmpty()) {
        div {
            css {
                paddingLeft = 20.px
            }
            RoomListEntries {
                rooms = props.childRooms
                small = props.small
            }
        }
    }
}

val RoomList = FC<RoomListProps> {props ->
    val rooms = useRooms()
    val layoutMode = use(LayoutModeContext)!!
    val myUser = useMyUser()
    var createMenuOpen by useState(false)

    val iconSize = if(props.small) 36.px else 48.px

    Stack {
        direction = FlexDirection.column
        css {
            gap = 4.px
        }

        // Let extensions override room entries rendering entirely
        val handled = ClientExtension.enabled.any { ext ->
            ext.renderSidebarRoomEntries(rooms, props.small, this)
        }
        if (!handled) {
            if (rooms.values.none { it.isGroup }) {
                RoomListEntries {
                    this.rooms = rooms.values.toList()
                    small = props.small
                }
            } else {
                // Two-level hierarchy: groups with their children indented, then
                // ungrouped rooms. Rooms whose parent is not visible to this viewer
                // count as top-level rather than silently disappearing.
                val visibleParents = rooms.values.filter { it.isGroup }.map { it.id }.toSet()
                rooms.values.filter { it.parentRoom?.id !in visibleParents }.forEach { room ->
                    if (room.isGroup) {
                        GroupRoomEntry {
                            group = room
                            childRooms = rooms.values.filter { it.parentRoom?.id == room.id }
                            small = props.small
                            canCreateInGroup = props.canCreate &&
                                room.hasPermission(myUser, RoomPermission.ROOM_OWNER)
                        }
                    } else {
                        RoomListEntries {
                            this.rooms = listOf(room)
                            small = props.small
                        }
                    }
                }
            }
        }

        if (props.canCreate) {
            button {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    gap = 12.px
                    fontFamily = sansSerif
                    alignItems = AlignItems.center
                    fontSize = if (props.small) 15.px else 18.px
                    lineHeight = if (props.small) 17.px else 20.px
                    color = Color("#222222")
                    padding = Padding(5.px, if (layoutMode == LayoutMode.PHONE) 20.px else 5.px)
                    margin = if (layoutMode == LayoutMode.PHONE) 0.px else Margin(0.px, (-5).px)
                    background = None.none
                    border = None.none
                    borderRadius = if (layoutMode == LayoutMode.PHONE) 0.px else 5.px
                    cursor = Cursor.pointer
                    textAlign = TextAlign.left
                    hover {
                        backgroundColor = rgb(0,0,0, 0.05)
                    }
                }
                onClick = { createMenuOpen = true }
                CreateEntryIcon { size = iconSize }
                span {
                    +"Create new…"
                }
            }
            DialogMenu {
                open = createMenuOpen
                onClose = { createMenuOpen = false }
                DialogMenuNav {
                    text = "Room"
                    description = "A space where a team predicts and discusses a set of questions."
                    navigate = "/new_room"
                }
                DialogMenuNav {
                    text = "Group"
                    description = "A container of rooms that shares its members with them and shows their combined scores."
                    navigate = "/new_group"
                }
            }
        }
    }
}
