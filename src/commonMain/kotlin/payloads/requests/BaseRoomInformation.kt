package payloads.requests

/**
 * Used to create a new room (defined in POST URL) or edit its information
 */
@kotlinx.serialization.Serializable
data class BaseRoomInformation(
    val name: String,
    val description: String,
)
