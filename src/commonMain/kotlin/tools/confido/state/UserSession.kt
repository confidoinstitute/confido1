package tools.confido.state

import kotlinx.serialization.Serializable
import users.User

@Serializable
data class UserSession(
    var user: User?,
    var language: String,
    // Beware: This is sent to the client
)
