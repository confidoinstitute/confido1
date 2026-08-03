package tools.confido.store

import tools.confido.state.serverState
import users.LoginLink

/**
 * Login (magic) links — the Store<E> pilot conversion: a small resident store with one
 * unique secondary index, establishing the pattern the remaining resident managers will
 * follow. (Like the manager it replaces, it does not expiry-sweep; expired links are
 * rejected at use time and deleted after use.)
 */
object loginLinkStore : MongoStore<LoginLink>(
    serverState.database.getCollection("loginLinks"),
    { e, id -> e.copy(id = id) },
) {
    val byToken = UniqueIndex(this) { it.token }
}

/** Register link stores. Called once from server startup. */
fun registerLinkStores() {
    Stores.register(loginLinkStore)
    Stores.resident[LoginLink::class] = loginLinkStore
}
