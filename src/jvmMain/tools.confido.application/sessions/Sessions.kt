package tools.confido.application.sessions

import io.ktor.server.application.*
import io.ktor.util.*
import tools.confido.state.UserSession

internal val SessionStorageTransient = AttributeKey<java.util.concurrent.ConcurrentHashMap<String, TransientData>>("SessionStorageTransient")

/**
 * A simple session plugin, not to be confused with the Ktor Sessions plugin.
 */
val Sessions = createRouteScopedPlugin("SessionHandler") {
    application.attributes.put(SessionTrackerKey, SessionTracker())
    // To add persistence to user session data, implement a new SessionStorage and change it here:
    application.attributes.put(SessionStorageTransient, java.util.concurrent.ConcurrentHashMap<String, TransientData>())

    onCall { call ->
        val tracker = call.application.attributes[SessionTrackerKey]
        tracker.load(call)
    }

    onCallRespond { call ->
        val tracker = call.application.attributes[SessionTrackerKey]
        tracker.send(call)
    }
}