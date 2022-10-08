package rooms

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.eqid.Entity
import tools.confido.question.Question
import users.User
import users.UserType

@Serializable
class Room(
    @SerialName("_id")
    override val id: String,
    val name: String,
    val createdAt: Instant,
    val description: String = "",
    val questions: MutableList<Question> = mutableListOf(),
    val members: MutableList<RoomMembership> = mutableListOf(),
    val inviteLinks: MutableList<InviteLink> = mutableListOf(),
) : Entity<String> {
    fun getQuestion(id: String): Question? {
        return questions.find { it.id == id }
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
        return members.find {
            it.user.eqid(user) && it.invitedVia?.canAccess ?: true && it.role.hasPermission(permission)
        } != null
    }
}