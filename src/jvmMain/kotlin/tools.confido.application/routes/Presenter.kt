package tools.confido.application.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import tools.confido.application.sessions.modifyUserSession
import tools.confido.state.PresenterInfo
import tools.confido.state.Topics
import tools.confido.state.PresenterView
import tools.confido.utils.unixNow
import tools.confido.state.ServerEntityLookup

fun presenterRoutes(routing: Routing) = routing.apply {
    // Presence-only topic: tracks open presenter windows via subscription lifetime.
    // The flow never emits; its collection lifetime is the signal.
    flowWS<Unit>(Topics.presenterTrack) {
        call.withUser {
            flow {
                transientData.addPresenterWindows(+1)
                call.application.log.info("new presenter")
                try {
                    awaitCancellation()
                } finally {
                    call.application.log.info("presenter closed")
                    transientData.addPresenterWindows(-1)
                }
            }
        }
    }

    postST("/presenter/stop") {
        call.withUser {
            call.modifyUserSession { it.copy(presenterInfo = null) }
            call.respond(HttpStatusCode.OK)
        }
    }

    postST("/presenter/set") {
        call.withUser {
            val view = call.receive<PresenterView>()
            if (!view.isValid(ServerEntityLookup)) badRequest("View is not valid")
            call.modifyUserSession {
                val oldInfo = it.presenterInfo ?: PresenterInfo()
                val newInfo = oldInfo.copy(view = view, lastUpdate = unixNow())
                it.copy(presenterInfo = newInfo)
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}
