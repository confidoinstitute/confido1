package payloads.responses

import kotlinx.serialization.Serializable
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
    val allowAnonymous: Boolean,
    val targetUserType: UserType = UserType.GUEST,
    val requireNickname: Boolean = false,
    val preventDuplicateNicknames: Boolean = false,
)
