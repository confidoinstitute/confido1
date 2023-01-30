package rooms

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.question.Question
import tools.confido.refs.*
import tools.confido.utils.generateId
import users.User
import users.UserType

@Serializable
data class Room(
    @SerialName("_id")
    override val id: String = "",
    val name: String,
    val createdAt: Instant = Clock.System.now(),
    val description: String = "",
    val questions: List<Ref<Question>> = emptyList(),
    val members: List<RoomMembership> = emptyList(),
    val inviteLinks: List<InviteLink> = emptyList(),
) : ImmediateDerefEntity {

    fun findLink(id: String?): InviteLink? {
        if (id == null || id == "") {
            return null
        }
        return inviteLinks.firstOrNull { it.id == id }
    }

    fun userRole(user: User?): RoomRole? {
        if (user == null) return null
        if (user.type == UserType.ADMIN) return Owner
        return members.find { user eqid it.user }?.role
    }

    fun hasPermission(user: User?, permission: RoomPermission): Boolean {
        if (user == null) {
            // Public rooms can be added here.
            return false
        }

        if (user.type == UserType.ADMIN) {
            return true
        }

        // Note that one user could have multiple memberships here.
        return members.any {
            it.user eqid user && findLink(it.invitedVia)?.canAccess ?: true && it.role.hasPermission(permission)
        }
    }
}

@Serializable
enum class InviteLinkState {
    ENABLED,
    DISABLED_JOIN,
    DISABLED_FULL,
}

@Serializable
data class InviteLink(
    @SerialName("_id")
    override val id: String = generateId(),
    val token: String,
    val description: String,
    /* Role granted by the invite link. */
    val role: RoomRole,
    /* User who created the invite link. */
    val createdBy: Ref<User>,
    /* Time of creation of the invite link. */
    val createdAt: Instant,
    /* Indicates whether guests are anonymous. */
    val allowAnonymous: Boolean = false,
    /* Indicates whether this link can be used by new users. */
    val state: InviteLinkState = InviteLinkState.ENABLED
) : HasId {
    fun link(origin: String, room: Room) = "$origin/room/${room.id}/invite/$token"

    val canJoin get() = state == InviteLinkState.ENABLED
    val canAccess get() = state != InviteLinkState.DISABLED_FULL
}

@Serializable
data class RoomMembership(
    val user: Ref<User>,
    val role: RoomRole,
    val invitedVia: String? = null,
)

@Serializable
enum class ExportHistory {
    LAST,
    DAILY,
    FULL,
}
