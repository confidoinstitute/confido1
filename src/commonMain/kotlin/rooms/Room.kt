package rooms

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.refs.Entity
import tools.confido.refs.Ref
import tools.confido.refs.eqid
import tools.confido.question.Question
import tools.confido.refs.ImmediateDerefEntity
import users.User
import users.UserType

@Serializable
data class Room(
    @SerialName("_id")
    override val id: String,
    val name: String,
    val createdAt: Instant,
    val description: String = "",
    val questions: List<Ref<Question>> = emptyList(),
    val members: List<RoomMembership> = emptyList(),
    val inviteLinks: List<InviteLink> = emptyList(),
) : ImmediateDerefEntity {

    fun hasPermission(user: User?, permission: RoomPermission): Boolean {
        if (user == null) {
            // Public rooms can be added here.
            return false
        }

        if (user.type == UserType.ADMIN) {
            return true
        }

        // Note that one user could have multiple memberships here.
        return members.find {
            it.user eqid user && it.invitedVia?.canAccess ?: true && it.role.hasPermission(permission)
        } != null
    }
}