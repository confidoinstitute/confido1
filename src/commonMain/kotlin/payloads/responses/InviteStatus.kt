package payloads.responses

import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.refs.Ref

/**
 * Status of an invitation link returned by [payloads.requests.CheckInvite]:
 *
 * - Is the invitation [valid]?
 * - The [room name][roomName] given to the client as they cannot access it yet
 * - If a new user uses the invitation link, [can][allowAnonymous] they join without e-mail?
 */
@Serializable
data class InviteStatus(
    val valid: Boolean,
    val roomName: String?,
    val roomRef: Ref<Room>?,
    val allowAnonymous: Boolean,
)
