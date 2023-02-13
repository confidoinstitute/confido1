package payloads.responses

import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.refs.Ref

@Serializable
data class InviteStatus(
    val valid: Boolean,
    val roomName: String?,
    val roomRef: Ref<Room>?,
    val allowAnonymous: Boolean,
)
