package users

import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.refs.ImmediateDerefEntity
import tools.confido.refs.Ref
import tools.confido.utils.generateToken

@Serializable
data class User(
    @SerialName("_id")
    override val id: String,
    val type: UserType,
    // TODO(privacy): make sure it does not get sent to the client for other users
    val email: String?,
    val emailVerified: Boolean = false,
    val nick: String?,
    // TODO(security): make sure it does not get sent to the client
    val password: String?,
    val createdAt: Instant,
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
    val token: String = generateToken(),
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
    val token: String = generateToken(),
    val user: Ref<User>,
    val email: String,
    val expiryTime: Instant,
) : ImmediateDerefEntity {
    fun isExpired() = now() > expiryTime

    fun link(origin: String) = "$origin/email_verify?t=$token"
}