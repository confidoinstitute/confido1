package tools.confido.users

import kotlinx.datetime.Clock
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.confido.refs.ExpiringEntity
import tools.confido.refs.ImmediateDerefEntity
import tools.confido.refs.Ref
import tools.confido.refs.ServerImmediateDerefEntity
import tools.confido.state.UserSessionValidity
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

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
    val active: Boolean = true,
    /** Small personal UI preferences keyed by a namespaced string (e.g. the
     *  per-room spoiler lens, "roomSpoilers:<roomId>" → "shown"). Owner-only:
     *  it travels on the self slice and is censored out everywhere else. */
    val uiSettings: Map<String, String> = emptyMap(),
    // Server-only deref: clients hold no user collection (§5.3) — the viewer's own
    // user travels on the self slice, all other identities as UserPresentations.
) : ServerImmediateDerefEntity {
    fun isAnonymous(): Boolean {
        return email == null
    }

    val displayName: String
        get() = when {
            type == UserType.GHOST -> "Deleted user"
            nick != null -> nick
            else -> "Anonymous user"
        }
}

/**
 * A potentially pseudonimized way of presenting a user identity to a given observer.
 */
@Serializable
data class UserPresentation(
    val isMyself: Boolean,
    /** Under a masked attribution policy this is ALWAYS the masked form (alias or
     *  anonymous placeholder) — piercing never substitutes it (see [realIdentity]). */
    val displayName: String? = null,
    val userId: Ref<User>? = null,
    val color: String? = null,
    /** The real identity behind a masked [displayName], carried only to observers
     *  holding [tools.confido.rooms.RoomPermission.VIEW_REAL_IDENTITIES] where the
     *  policy's moderatorAccess allows. An *annotation*, never a substitution:
     *  audience-facing surfaces (presenter views) render [displayName] only, so a
     *  pierced presentation can never leak onto a projector by construction. */
    val realIdentity: String? = null,
)

@Serializable
data class LoginLink(
    @SerialName("_id")
    override val id: String = "", // generated on insert
    val token: String,
    val user: Ref<User>,
    override val expiryTime: Instant,
    val url: String = "/",
    val sentToEmail: String? = null,
    val validity: UserSessionValidity = UserSessionValidity.PERMANENT,
) : ImmediateDerefEntity, ExpiringEntity {

    fun link(origin: String) = "$origin/email_login?t=$token"
}

@Serializable
data class EmailVerificationLink(
    @SerialName("_id")
    override val id: String = "", // generated on insert
    val token: String,
    val user: Ref<User>,
    val email: String,
    override val expiryTime: Instant,
) : ImmediateDerefEntity, ExpiringEntity {

    fun link(origin: String) = "$origin/email_verify?t=$token"
}

/**
 * Mailed to the previous address after an e-mail change ("not you?"): restores [email]
 * and locks the account down. Using one cancels only links created after it — those
 * stem from later changes, possibly an intruder's — so the owner's (earliest) link
 * always wins. Kept after use ([usedAt]) so repeated opens stay harmless successes.
 */
@Serializable
data class EmailRevertLink(
    @SerialName("_id")
    override val id: String = "", // generated on insert
    val token: String,
    val user: Ref<User>,
    val email: String,
    val createdAt: Instant,
    override val expiryTime: Instant,
    val usedAt: Instant? = null,
) : ImmediateDerefEntity, ExpiringEntity {

    fun link(origin: String) = "$origin/email_revert?t=$token"
}

@Serializable
data class PasswordResetLink(
    @SerialName("_id")
    override val id: String = "", // generated on insert
    val token: String,
    val user: Ref<User>,
    override val expiryTime: Instant,
) : ImmediateDerefEntity, ExpiringEntity {

    fun link(origin: String) = "$origin/password_reset?t=$token"
}

@Serializable
data class DeleteUserOptions(
    val deleteComments: Boolean = false, // if false, comments will be anonymized
)

/** How long the links mailed for these flows stay valid. */
val EMAIL_VERIFICATION_VALIDITY = 24.hours
val PASSWORD_RESET_VALIDITY = 1.hours
/** The "your password/e-mail was changed — not you?" links: long, the owner may read mail late. */
val PASSWORD_CHANGED_UNDO_VALIDITY = 7.days
val EMAIL_CHANGE_REVERT_VALIDITY = 7.days

/** Deliberately loose (something@something.tld): the verification mail is the real check. */
fun isEmailValid(mail: String): Boolean = Regex(".+@.+\\..+").matches(mail)

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

    return PasswordCheckResult.OK
}

/**
 * Sanitize a nickname for storage:
 * - Remove control characters
 * - Normalize internal whitespace runs to single space
 * - Trim leading/trailing whitespace
 */
fun sanitizeNick(nick: String): String =
    nick.replace(Regex("[\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]"), "") // Remove control chars
        .replace(Regex("\\s+"), " ") // Normalize whitespace runs
        .trim() // Remove leading/trailing whitespace

/**
 * Normalize a nickname for comparison:
 * - Convert to lowercase
 * (More normalization rules can be added here in the future)
 */
fun normalizeNickForComparison(nick: String): String =
    nick.lowercase()

/**
 * Compare two nicknames for equality after sanitization and normalization
 */
fun compareNicks(nick1: String?, nick2: String?): Boolean {
    if (nick1 == null || nick2 == null) return false
    return normalizeNickForComparison(sanitizeNick(nick1)) == normalizeNickForComparison(sanitizeNick(nick2))
}
