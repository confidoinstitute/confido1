package users

import IdentifiedById
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val type: UserType,
    // TODO(privacy): make sure it does not get sent to the client for other users
    val email: String?,
    val emailVerified: Boolean = false,
    val nick: String?,
    // TODO(security): make sure it does not get sent to the client
    val password: String?,
    val createdAt: Instant,
    val lastLoginAt: Instant,
)