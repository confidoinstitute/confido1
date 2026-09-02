package tools.confido.payloads.requests

import kotlinx.serialization.Serializable
import tools.confido.rooms.AttributionPolicy
import tools.confido.rooms.RoomColor
import tools.confido.rooms.RoomEDT
import tools.confido.rooms.RoomEDTSerializer
import tools.confido.rooms.ScoreboardMode
import tools.confido.rooms.ScoringConfig
import tools.confido.extensions.ExtensionData
import tools.confido.question.QuestionSchedule
import tools.confido.refs.Ref
import tools.confido.rooms.Room

/**
 * Used to create a new room (defined in POST URL) or edit its information
 */
@Serializable
data class BaseRoomInformation(
    val name: String,
    val description: String,
    val color: RoomColor,
    val icon: String?,
    val defaultSchedule: QuestionSchedule = QuestionSchedule(),
    val scoring: ScoringConfig? = null,
    val commentTemplate: String = "",
    @Serializable(with = RoomEDTSerializer::class)
    val extensionData: ExtensionData = ExtensionData(RoomEDT),
    // Appended after extensionData: existing callers construct this positionally.
    val defaultAttribution: AttributionPolicy = AttributionPolicy(),
    val pseudonymPool: List<String>? = null,
    /** Create the room as a group container. Immutable after creation — roomEdit
     *  ignores it (a room that has accumulated questions cannot become a group,
     *  nor vice versa). */
    val isGroup: Boolean = false,
    /** Parent group assignment; requires ROOM_OWNER in the target group. */
    val parentRoom: Ref<Room>? = null,
)
