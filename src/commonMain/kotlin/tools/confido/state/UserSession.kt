package tools.confido.state

import kotlinx.serialization.Serializable
import tools.confido.refs.Ref
import tools.confido.refs.maybeDeref
import users.User

@Serializable
data class UserSession(
    var userRef: Ref<User>?,
    var language: String,
    // Beware: This is sent to the client
)
