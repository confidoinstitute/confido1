package payloads.requests

import kotlinx.serialization.Serializable

@Serializable
data class CheckInvite(
    val roomId: String,
    val inviteToken: String,
)