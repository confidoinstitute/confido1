package payloads.requests

import kotlinx.serialization.Serializable

@Serializable
data class SetNick(
    val name: String,
)

@Serializable
data class StartEmailVerification (
    val email: String,
)

@Serializable
data class EmailVerification (
    val token: String,
)