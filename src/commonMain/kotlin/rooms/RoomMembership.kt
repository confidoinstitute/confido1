package rooms

import kotlinx.serialization.Serializable
import users.User

@Serializable
data class RoomMembership(
    val user: User,
    val role: RoomRole,
    val invitedVia: InviteLink?,
)