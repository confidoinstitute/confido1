package payloads.requests

import kotlinx.serialization.Serializable

@Serializable
data class AcceptInviteAndCreateUser(
    val roomId: String,
    val inviteToken: String,
    val userNick: String?,
    val email: String?,
)