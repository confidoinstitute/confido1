package tools.confido.store

import tools.confido.state.serverState
import tools.confido.users.EmailRevertLink
import tools.confido.users.EmailVerificationLink
import tools.confido.users.LoginLink
import tools.confido.users.PasswordResetLink

/**
 * Token link stores: small resident stores with one unique secondary index each.
 * (Like the managers they replace, they do not expiry-sweep; expired links are rejected
 * at use time and deleted after use or on user deletion.)
 */
object loginLinkStore : MongoStore<LoginLink>(
    serverState.database.getCollection("loginLinks"),
    { e, id -> e.copy(id = id) },
) {
    val byToken = UniqueIndex(this) { it.token }
}

object verificationLinkStore : MongoStore<EmailVerificationLink>(
    serverState.database.getCollection("mailVerificationLinks"),
    { e, id -> e.copy(id = id) },
) {
    val byToken = UniqueIndex(this) { it.token }
}

object emailRevertLinkStore : MongoStore<EmailRevertLink>(
    serverState.database.getCollection("emailRevertLinks"),
    { e, id -> e.copy(id = id) },
) {
    val byToken = UniqueIndex(this) { it.token }
}

object passwordResetLinkStore : MongoStore<PasswordResetLink>(
    serverState.database.getCollection("passwordResetLinks"),
    { e, id -> e.copy(id = id) },
) {
    val byToken = UniqueIndex(this) { it.token }
}

/** Register link stores. Called once from server startup. */
fun registerLinkStores() {
    listOf(loginLinkStore, verificationLinkStore, emailRevertLinkStore, passwordResetLinkStore).forEach { Stores.register(it) }
    Stores.resident[LoginLink::class] = loginLinkStore
    Stores.resident[EmailVerificationLink::class] = verificationLinkStore
    Stores.resident[PasswordResetLink::class] = passwordResetLinkStore
    Stores.resident[EmailRevertLink::class] = emailRevertLinkStore
}
