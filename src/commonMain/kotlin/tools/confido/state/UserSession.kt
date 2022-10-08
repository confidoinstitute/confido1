package tools.confido.state

import kotlinx.serialization.Serializable
import tools.confido.refs.*
import users.User

@Serializable
data class UserSession(
    var userRef: Ref<User>? = null,
    var language: String = "en",
    var presenterActive: Int = 0,
    var presenterView: PresenterView? = null,
    // Beware: This is sent to the client
) {
    // this is done often enough to warrant a shortcut
    val user: User?
        get() = userRef?.deref()
}
