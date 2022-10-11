package payloads.requests

import kotlinx.serialization.Serializable

@Serializable
data class PasswordLogin(
    val email: String,
    val password: String,
)

@Serializable
data class EmailLogin(
    val token: String,
)

@Serializable
data class SendMailLink(
    val email: String,
)