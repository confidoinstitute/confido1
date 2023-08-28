package payloads.requests

import kotlinx.serialization.Serializable
import rooms.RoomRole
import tools.confido.refs.Ref
import tools.confido.state.UserSessionValidity
import users.User

/**
 * Accept invitation identified by [inviteToken] by an already logged-in user.
 */
@Serializable
data class AcceptInvite(
    val inviteToken: String,
)

/**
 * Accept invitation identified by [inviteToken] by a not logged in client and create an user with given [userNick] and [e-mail].
 */
@Serializable
data class AcceptInviteAndCreateUser(
    val inviteToken: String,
    val userNick: String?,
    val email: String?,
    val validity: UserSessionValidity,
)

/**
 * Create a new room invitation link with given properties. The room is defined by the POST URL.
 */
@Serializable
data class CreateNewInvite(
    val description: String?,
    val role: RoomRole,
    val anonymous: Boolean,
)

/**
 * Delete a given invitation link. If [keepUsers] is set, make all members joined via it permanent. Otherwise revoke their membership.
 */
@Serializable
data class DeleteInvite(
    val id: String,
    val keepUsers: Boolean,
)

/**
 * Sum data type for an added room member with a given [role].
 */
@Serializable
sealed class AddedMember {
    abstract val role: RoomRole
}

/**
 * Add a new member with a given [e-mail].
 */
@Serializable
data class AddedNewMember(
    val email: String,
    override val role: RoomRole,
) : AddedMember()

/**
 * Add already existing [user] as a member.
 */
@Serializable
data class AddedExistingMember(
    val user: Ref<User>,
    override val role: RoomRole,
) : AddedMember()
