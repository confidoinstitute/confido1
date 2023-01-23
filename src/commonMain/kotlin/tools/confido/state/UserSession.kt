package tools.confido.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.refs.*
import users.User

@Serializable
data class UserSession(
    @SerialName("_id")
    override val id: String = "",
    val userRef: Ref<User>? = null,
    val language: String = "en",
    val presenterInfo: PresenterInfo? = null,
) : ImmediateDerefEntity {
    // this is done often enough to warrant a shortcut
    val user: User?
        get() = userRef?.deref()
}
