package payloads.requests

import kotlinx.serialization.Serializable
import rooms.RoomRole
import tools.confido.refs.Ref
import users.User

@Serializable
data class AcceptInvite(
    val inviteToken: String,
)

@Serializable
data class AcceptInviteAndCreateUser(
    val inviteToken: String,
    val userNick: String?,
    val email: String?,
)

@Serializable
data class CreateNewInvite(
    val description: String?,
    val role: RoomRole,
    val anonymous: Boolean,
)

@Serializable
data class CheckInvite(
    val inviteToken: String,
)

@Serializable
sealed class AddedMember {
    abstract val role: RoomRole
}

@Serializable
data class AddedNewMember(
    val email: String,
    override val role: RoomRole,
) : AddedMember()

@Serializable
data class AddedExistingMember(
    val user: Ref<User>,
    override val role: RoomRole,
) : AddedMember()