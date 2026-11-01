package tools.confido.payloads.requests

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

/** Set (or clear, with a null [value]) one [tools.confido.users.User.uiSettings] entry. */
@Serializable
data class SetUiSetting(
    val key: String,
    val value: String?,
)

/**
 * Initiate [e-mail][email] verification process.
 */
@Serializable
data class StartEmailVerification (
    val email: String,
)

/**
 * Verify a given e-mail verification [token] with the server.
 */
@Serializable
data class TokenVerification (
    val token: String,
)

/**
 * Finish a password reset: the reset link's [token] authorizes setting [newPassword]
 * (no current password needed, no session needed).
 */
@Serializable
data class PasswordResetFinish(
    val token: String,
    val newPassword: String,
)