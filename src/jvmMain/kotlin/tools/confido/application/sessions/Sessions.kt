package tools.confido.application.sessions

import io.ktor.server.application.*
import io.ktor.util.*
import tools.confido.state.UserSession

internal val SessionStorageUserSession = AttributeKey<SessionStorage<UserSession>>("SessionStorageUserSession")
internal val SessionStorageTransient = AttributeKey<SessionStorage<TransientData>>("SessionStorageTransient")

val Sessions = createRouteScopedPlugin("SessionHandler") {
    application.attributes.put(SessionTrackerKey, SessionTracker())
    application.attributes.put(SessionStorageUserSession, MemorySessionStorage<UserSession>())
    application.attributes.put(SessionStorageTransient, MemorySessionStorage<TransientData>())

    onCall { call ->
        val tracker = call.application.attributes[SessionTrackerKey]
        tracker.load(call)
    }

    onCallRespond { call ->
        val tracker = call.application.attributes[SessionTrackerKey]
        tracker.send(call)
    }
}