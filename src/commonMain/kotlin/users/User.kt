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

@Serializable
data class PasswordUndoLink(
    @SerialName("_id")
    override val id: String = "", // generated on insert
    val token: String,
    val user: Ref<User>,
    val expiryTime: Instant,
) : ImmediateDerefEntity {
    fun isExpired() = now() > expiryTime

    fun link(origin: String) = "$origin/password_undo?t=$token"
}

enum class PasswordCheckResult {
    OK,
    TOO_SHORT,
    TOO_LONG,
}

const val MIN_PASSWORD_LENGTH = 8
// Setting a max password length prevents DoS attacks by providing very long passwords.
// With password4j's Argon2 implementation, the slowdown becomes noticeable at lengths of 100,000 characters and more.
// We choose a conservative limit that is significantly longer than anyone is likely to ever try, even with password
// managers.
const val MAX_PASSWORD_LENGTH = 4096

fun checkPassword(password: String): PasswordCheckResult {
    if (password.length < MIN_PASSWORD_LENGTH) {
        return PasswordCheckResult.TOO_SHORT
    }
    if (password.length > MAX_PASSWORD_LENGTH) {
        return PasswordCheckResult.TOO_LONG
    }

    return PasswordCheckResult.OK;
}