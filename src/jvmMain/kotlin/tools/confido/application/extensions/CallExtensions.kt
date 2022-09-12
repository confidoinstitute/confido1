package tools.confido.application.extensions

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import tools.confido.state.UserSession

fun ApplicationCall.createNewSession() {
    val defaultSession = UserSession(name = null, language = "en")
    sessions.set(defaultSession)
}

var ApplicationCall.userSession: UserSession?
    get() = sessions.get()
    set(value) = sessions.set(value)