package payloads.requests

import kotlinx.serialization.Serializable

@Serializable
data class SetNick(
    val name: String,
)

@Serializable
data class SetPassword(
    val currentPassword: String?,
    val newPassword: String,
)

@Serializable
data class StartEmailVerification (
    val email: String,
)

// Used for different tokens: E-mail verification, password set undo and e-mail set undo.
@Serializable
data class TokenVerification (
    val token: String,
)