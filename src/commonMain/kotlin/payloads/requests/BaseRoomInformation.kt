package payloads.requests

import rooms.RoomColor
import tools.confido.question.QuestionSchedule

/**
 * Used to create a new room (defined in POST URL) or edit its information
 */
@kotlinx.serialization.Serializable
data class BaseRoomInformation(
    val name: String,
    val description: String,
    val color: RoomColor,
    val icon: String?,
    val defaultSchedule: QuestionSchedule = QuestionSchedule(),
)
