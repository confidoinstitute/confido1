package extensions.room_groups

import rooms.Room
import tools.confido.extensions.*
import tools.confido.refs.Ref

/** Whether this room is a group container (has child rooms, no questions of its own). */
val IsGroupKey = ExtensionDataKeyWithDefault<Boolean>("room_group.is_group", false)

/** The parent group room. Set on child rooms to indicate group membership. */
val ParentRoomKey = ExtensionDataKey<Ref<Room>?>("room_group.parent")

open class RoomGroupsExtension : Extension {
    override val extensionId = "room_groups"
    override fun registerEdtKeys(edt: ExtensionDataType) {
        when (edt.name) {
            "RoomEDT" -> {
                edt.add(IsGroupKey)
                edt.add(ParentRoomKey)
            }
        }
    }
}

// Convenience extension properties
val Room.isGroup: Boolean get() = extensionData[IsGroupKey]
val Room.parentRoomId: String? get() = extensionData.get(ParentRoomKey)?.id
