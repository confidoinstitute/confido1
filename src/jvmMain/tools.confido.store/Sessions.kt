package tools.confido.store

import tools.confido.state.UserSession
import tools.confido.state.serverState

object userSessionStore : ExpiringMongoStore<UserSession>(
    serverState.database.getCollection("userSessions"),
    { e, id -> e.copy(id = id) },
)

/** Register the session store. Called once from server startup. */
fun registerSessionStores() {
    Stores.register(userSessionStore)
    Stores.resident[UserSession::class] = userSessionStore
}
