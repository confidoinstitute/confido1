package payloads.responses

import kotlinx.serialization.Serializable

@Serializable
data class InviteStatus(
    val valid: Boolean,
    val roomName: String?,
)
