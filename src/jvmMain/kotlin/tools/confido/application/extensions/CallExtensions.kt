package tools.confido.application.extensions

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import tools.confido.state.UserSession

/**
 * Provides the user session data, initializing it to default values if no session exists.
 */
var ApplicationCall.userSession: UserSession
    get() {
        val session = sessions.get<UserSession>()
        return if (session == null) {
            val defaultSession = UserSession(name = null, language = "en")
            sessions.set(defaultSession)

            defaultSession
        } else {
            session
        }
    }
    set(value) {
        sessions.set(value)
    }