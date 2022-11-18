package users

import kotlinx.datetime.Clock
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.refs.ImmediateDerefEntity
import tools.confido.refs.Ref

@Serializable
data class User(
    @SerialName("_id")
    override val id: String = "",
    val type: UserType,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val nick: String? = null,
    val password: String? = null,
    val createdAt: Instant = Clock.System.now(),
    val lastLoginAt: Instant? = null,
    val active: Boolean = true
) : ImmediateDerefEntity {
    fun isAnonymous(): Boolean {
        return email == null
    }
}

@Serializable
data class LoginLink(
    @SerialName("_id")
    override val id: String = "", // generated on insert
    val token: String,
    val user: Ref<User>,
    val expiryTime: Instant,
    val url: String = "/",
    val sentToEmail: String? = null,
) : ImmediateDerefEntity {
    fun isExpired() = now() > expiryTime

    fun link(origin: String) = "$origin/email_login?t=$token"
}

@Serializable
data class EmailVerificationLink(
    @SerialName("_id")
    override val id: String = "", // generated on insert
    val token: String,
    val user: Ref<User>,
    val email: String,
    val expiryTime: Instant,
) : ImmediateDerefEntity {
    fun isExpired() = now() > expiryTime

    fun link(origin: String) = "$origin/email_verify?t=$token"
}