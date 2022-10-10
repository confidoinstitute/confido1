package rooms

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
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
    val description: String,
    /**
     * Role granted by the invite link.
     */
    val role: RoomRole,
    /**
     * User who created the invite link.
     */
    val createdBy: User,
    /**
     * Time of creation of the invite link.
     */
    val createdAt: Instant,
    /**
     * Indicates whether guests are anonymous.
     */
    val anonymous: Boolean,
    /**
     * Indicates whether this link can be used by new users.
     */
    val canJoin: Boolean = true,
    /**
     * Indicates whether users invited by this link can access the room.
     */
    val canAccess: Boolean = true,
) {
    // TODO: this is likely not secure
    val token = randomString(32)
}