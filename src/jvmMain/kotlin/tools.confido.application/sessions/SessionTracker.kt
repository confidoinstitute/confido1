package tools.confido.application.sessions

import io.ktor.server.application.*
import io.ktor.util.*
import tools.confido.refs.ref
import tools.confido.state.UserSession
import tools.confido.state.UserSessionValidity
import tools.confido.state.serverState
import users.User

internal val SessionTrackerKey = AttributeKey<SessionTracker>("SessionTrackerKey")

private fun newSessionId(): String {
    return generateNonce() + generateNonce()
}

/**
 * Create a new session.
 *
 * Does not work in websockets (a cookie cannot be set after a websocket is started).
 *
 * @return the id of the newly created session
 */
private fun ApplicationCall.createNewSession(): String {
    val tracker = application.attributes[SessionTrackerKey]
    return tracker.createNewSession(this)
}

/**
 * Provides the id of the current session.
 *
 * @return the id of the current session if any is active
 */
private val ApplicationCall.sessionId: String?
    get() {
        val tracker = application.attributes[SessionTrackerKey]
        return tracker.sessionId(this)
    }

/**
 * Provides the session id or creates a new session.
 *
 * Does not work in websockets (a cookie cannot be set after a websocket is started).
 *
 * @return the id of the current session
 */
fun ApplicationCall.getSessionIdOrCreateNew(): String {
    var id = sessionId
    if (id == null) {
        val tracker = application.attributes[SessionTrackerKey]
        id = tracker.createNewSession(this)
    }
    return id
}

/**
 * Provides access to user session data.
 *
 * In case a session does not exist, setting a value will create a new session.
 * In case the session has expired, ignore it.
 */
val ApplicationCall.userSession: UserSession?
    get() = sessionId?.let { serverState.userSessionManager.entityMap[sessionId]?.let {if (it.isExpired()) null else it} }

suspend fun ApplicationCall.setUserSession(value: UserSession?) {
    val id = getSessionIdOrCreateNew()
    if (value == null) {
        serverState.userSessionManager.deleteEntity(id, ignoreNonexistent = true)
        clearCookie(this)
    } else {
        serverState.userSessionManager.replaceEntity(value.copy(id = id), upsert = true)
    }
}


suspend fun ApplicationCall.modifyUserSession(modify: (UserSession) -> UserSession) =
    serverState.withMutationLock {
        val id = getSessionIdOrCreateNew()
        val oldSession = userSession ?: UserSession(id = id, validity = UserSessionValidity.TRANSIENT)
        val newSession = modify(oldSession).renew()
        setUserSession(newSession)
        newSession
    }

suspend fun ApplicationCall.loginUserSession(user: User, validity: UserSessionValidity) = modifyUserSession {
    it.copy(userRef = user.ref, validity = validity)
}
suspend fun ApplicationCall.renewUserSession() = modifyUserSession {it}

suspend fun ApplicationCall.destroyUserSession() = setUserSession(null)

/**
 * Provides access to short-lived session data, which will be lost upon server shutdown.
 *
 * There is no need to set an initial value as long as a session exists already,
 * a default value is created automatically on first access.
 *
 * In case a session does not exist, setting a value will create a new session.
 */
var ApplicationCall.transientUserData: TransientData?
    get() {
        val id = sessionId ?: return null
        val storage = application.attributes[SessionStorageTransient]
        return storage.getOrPut(id) { TransientData() }
    }
    set(value) {
        val id = getSessionIdOrCreateNew()
        val storage = application.attributes[SessionStorageTransient]
        if (value == null) {
            storage.remove(id)
        } else {
            storage[id] = value
        }
    }

/**
 * Keeps track of the current session id within a call and handles cookie updates.
 */
class SessionTracker {
    private val sessionIdKey = AttributeKey<String>("SessionId")

    suspend fun load(call: ApplicationCall) {
        val cookie = readCookie(call) ?: return

        if (serverState.userSessionManager.get(cookie) != null)
            call.attributes.put(sessionIdKey, cookie)
    }

    fun sessionId(call: ApplicationCall): String? {
        return call.attributes.getOrNull(sessionIdKey)
    }

    fun createNewSession(call: ApplicationCall): String {
        val sessionId = newSessionId()
        call.attributes.put(sessionIdKey, sessionId)

        return sessionId
    }

    fun send(call: ApplicationCall) {
        val session = call.userSession ?: return
        sendCookie(call, session.id, session.validity == UserSessionValidity.PERMANENT)
    }
}

