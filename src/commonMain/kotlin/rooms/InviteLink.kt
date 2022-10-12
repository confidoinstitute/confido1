package rooms

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import tools.confido.refs.HasId
import tools.confido.refs.Ref
import tools.confido.utils.generateId
import tools.confido.utils.generateToken
import tools.confido.utils.randomString
import users.User

@Serializable
enum class InviteLinkState {
    ENABLED,
    DISABLED_JOIN,
    DISABLED_FULL,
}

@Serializable
data class InviteLink(
    override val id: String = generateId(),
    val token: String = generateToken(),
    val description: String,
    /* Role granted by the invite link. */
    val role: RoomRole,
    /* User who created the invite link. */
    val createdBy: Ref<User>,
    /* Time of creation of the invite link. */
    val createdAt: Instant,
    /* Indicates whether guests are anonymous. */
    val anonymous: Boolean,
    /* Indicates whether this link can be used by new users. */
    val canJoin: Boolean = true,
    /**Indicates whether users invited by this link can access the room. */
    val canAccess: Boolean = true,
) : HasId {
    fun link(origin: String, room: Room) = "$origin/room/${room.id}/invite/$token"
}