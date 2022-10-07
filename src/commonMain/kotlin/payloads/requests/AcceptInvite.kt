package payloads.requests

import kotlinx.serialization.Serializable

@Serializable
data class AcceptInvite(
    val roomId: String,
    val inviteToken: String,
)