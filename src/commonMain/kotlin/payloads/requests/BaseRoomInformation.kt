package payloads.requests

import kotlinx.serialization.Serializable
import rooms.RoomColor
import rooms.RoomEDT
import rooms.RoomEDTSerializer
import rooms.ScoreboardMode
import rooms.ScoringConfig
import tools.confido.extensions.ExtensionData
import tools.confido.question.QuestionSchedule

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
)
