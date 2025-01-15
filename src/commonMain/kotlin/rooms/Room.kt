package rooms

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.extensions.ExtensionData
import tools.confido.extensions.ExtensionDataSerializer
import tools.confido.extensions.ExtensionDataType
import tools.confido.question.Question
import tools.confido.question.QuestionSchedule
import tools.confido.refs.*
import tools.confido.utils.generateId
import tools.confido.utils.HasUrlPrefix
import users.User
import users.UserType

enum class ScoreboardMode {
    NONE, PRIVATE, PUBLIC
}

@Serializable
data class ScoringConfig(
    val scoreboardMode: ScoreboardMode = ScoreboardMode.NONE,
) {
    fun identify() = "${scoreboardMode.toString()}"
}


val RoomEDT = ExtensionDataType("RoomEDT")
class RoomEDTSerializer: ExtensionDataSerializer(RoomEDT)

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
    val color: RoomColor = colorFromId(id),
    val icon: String? = null,
    val defaultSchedule: QuestionSchedule = QuestionSchedule(),
    val scoring: ScoringConfig? = null,
    @Serializable(with = RoomEDTSerializer::class)
    val extensionData: ExtensionData = ExtensionData(RoomEDT),
) : ImmediateDerefEntity, HasUrlPrefix {
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

    override val urlPrefix get() = urlPrefix(id)

    companion object {
        fun urlPrefix(id: String) = "/rooms/$id"

        private fun colorFromId(id: String): RoomColor {
            val base = id.fold(47) { acc, c ->
                (acc * 257 + c.code) % 65537
            }
            return RoomColor.values().let {
                it[base % it.size]
            }
        }
    }
}

@Serializable
enum class RoomColor {
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    CYAN,
    BLUE,
    MAGENTA,
    GRAY,
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
    /* Type of users created by this link (MEMBER or GUEST) */
    val targetUserType: UserType = UserType.GUEST,
    /* Whether to require nickname when joining */
    val requireNickname: Boolean = false,
    /* Whether to prevent duplicate nicknames */
    val preventDuplicateNicknames: Boolean = false,
    /* Indicates whether guests are anonymous. */
    val allowAnonymous: Boolean = false,
    /* Indicates whether this link can be used by new users. */
    val state: InviteLinkState = InviteLinkState.ENABLED
) : HasId {
    init {
        // Validate that only MEMBER or GUEST types are allowed for invite links
        require(targetUserType == UserType.MEMBER || targetUserType == UserType.GUEST) {
            "Invite links can only create MEMBER or GUEST users"
        }
    }

    fun link(origin: String, room: Room) = "$origin/join/$token"

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
    LAST_SCORED,
    DAILY,
    FULL,
}
