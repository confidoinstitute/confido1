package payloads.requests

import kotlinx.serialization.Serializable

/**
 * Log in via [email] and [password] credentials.
 */
@Serializable
data class PasswordLogin(
    val email: String,
    val password: String,
)

/**
 * Finish log in via e-mail procedure by verifying the received [token].
 */
@Serializable
data class EmailLogin(
    val token: String,
)

/**
 * Initiate log in via [e-mail][email]. After logging in, redirect client to a given [url].
 */
@Serializable
data class SendMailLink(
    val email: String,
    val url: String,
)