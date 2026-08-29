package tools.confido.payloads.requests

import kotlinx.serialization.Serializable

@kotlinx.serialization.Serializable
data class EditProfile(
    val nick: String,
    val email: String,
    val currentPassword: String?,
    val newPassword: String?,
)

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
 * Verify a given [token] with the server and perform its associated action.
 *
 * Used for different tokens:
 * - E-mail verification
 * - Password reset
 */
@Serializable
data class TokenVerification (
    val token: String,
)