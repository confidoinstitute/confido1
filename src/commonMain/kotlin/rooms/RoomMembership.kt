package rooms

import kotlinx.serialization.Serializable
import tools.confido.refs.Ref
import users.User

@Serializable
data class RoomMembership(
    val user: Ref<User>,
    val role: RoomRole,
    val invitedVia: String?,
)