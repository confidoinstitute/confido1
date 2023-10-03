package payloads.requests

import kotlinx.serialization.Serializable
import tools.confido.refs.Ref
import tools.confido.state.UserSessionValidity
import users.User

/**
 * Log in via [email] and [password] credentials.
 */
@Serializable
data class PasswordLogin(
    val email: String,
    val password: String,
    val validity: UserSessionValidity,
)

/**
 * Finish log in via e-mail procedure by verifying the received [token].
 */
@Serializable
data class EmailLogin(
    val token: String,
)

/**
 * Login via [username] (dev or demo mode).
 */
@Serializable
data class UsernameLogin(
    val username: Ref<User>,
    val validity: UserSessionValidity,
)

/**
 * Initiate log in via [e-mail][email]. After logging in, redirect client to a given [url].
 */
@Serializable
data class SendMailLink(
    val email: String,
    val url: String,
    val validity: UserSessionValidity,
)