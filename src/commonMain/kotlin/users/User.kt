package users

import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import tools.confido.eqid.IdentifiedById
import tools.confido.utils.randomString

@Serializable
data class User(
    override val id: String,
    val type: UserType,
    // TODO(privacy): make sure it does not get sent to the client for other users
    val email: String?,
    val emailVerified: Boolean = false,
    val nick: String?,
    // TODO(security): make sure it does not get sent to the client
    val password: String?,
    val createdAt: Instant,
    val lastLoginAt: Instant,
) : IdentifiedById<String>

@Serializable
data class LoginLink (
    val user: User,
    val expiryTime: Instant,
) {
    // TODO: Make this cryptographically secure
    val token = randomString(32)

    fun isExpired() = now() > expiryTime

    fun link(origin: String) = "$origin/email_login?t=$token"
}
