package payloads.requests

import kotlinx.serialization.Serializable
import rooms.RoomRole
import tools.confido.refs.Ref
import users.User

@Serializable
data class AcceptInvite(
    val roomId: String,
    val inviteToken: String,
)

@Serializable
data class AcceptInviteAndCreateUser(
    val roomId: String,
    val inviteToken: String,
    val userNick: String?,
    val email: String?,
)

@Serializable
data class CreateNewInvite(
    val roomId: String,
    val description: String?,
    val role: RoomRole,
    val anonymous: Boolean,
)

@Serializable
data class AddMember(
    val user: Ref<User>,
    var role: RoomRole,
)