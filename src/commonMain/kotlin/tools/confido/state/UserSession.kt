package tools.confido.state

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    var name: String?,
    var language: String,
    // Beware: This is sent to the client; if you want to store sensitive
    // information in the session, you will need to create a separate class
)
