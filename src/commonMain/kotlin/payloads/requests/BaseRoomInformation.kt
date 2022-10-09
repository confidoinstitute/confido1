package payloads.requests

@kotlinx.serialization.Serializable
data class BaseRoomInformation(
    val name: String,
    val description: String,
)
