package tools.confido.state

import kotlinx.datetime.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.refs.*
import tools.confido.serialization.CoercingNullSerializer
import users.User
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

enum class UserSessionValidity(val validTime: Duration) {
    TRANSIENT(3.hours),
    PERMANENT(90.days);

    companion object {
        fun fromBool(permanent: Boolean) = when(permanent) {
            true -> PERMANENT
            false -> TRANSIENT
        }
    }
}

object PresenterInfoSerializer: CoercingNullSerializer<PresenterInfo>(PresenterInfo.serializer()) {}

@Serializable
data class UserSession(
    @SerialName("_id")
    override val id: String = "",
    val userRef: Ref<User>? = null,
    val language: String = "en",
    @Serializable(with=PresenterInfoSerializer::class)
    val presenterInfo: PresenterInfo? = null,
    val validity: UserSessionValidity = UserSessionValidity.PERMANENT,
    override val expiryTime: Instant = Clock.System.now() + validity.validTime,
) : ImmediateDerefEntity, ExpiringEntity {
    // this is done often enough to warrant a shortcut
    val user: User?
        get() = userRef?.deref()
    fun renew() = copy(expiryTime = Clock.System.now() + validity.validTime)

}
