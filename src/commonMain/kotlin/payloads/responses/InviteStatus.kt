package payloads.responses

import kotlinx.serialization.Serializable
import rooms.InviteFieldPolicy
import rooms.Room
import rooms.RoomColor
import tools.confido.refs.Ref
import users.UserType

@Serializable
data class InviteStatus(
    val valid: Boolean,
    val roomName: String?,
    val roomRef: Ref<Room>?,
    val roomColor: RoomColor?,
    val nicknamePolicy: InviteFieldPolicy = InviteFieldPolicy.OPTIONAL,
    val emailPolicy: InviteFieldPolicy = InviteFieldPolicy.OPTIONAL,
    val targetUserType: UserType = UserType.GUEST,
    val preventDuplicateNicknames: Boolean = false,
)
