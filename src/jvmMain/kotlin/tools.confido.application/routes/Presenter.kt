package tools.confido.application.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.withTimeout
import payloads.responses.WSData
import payloads.responses.WSError
import payloads.responses.WSErrorType
import rooms.RoomPermission
import tools.confido.application.sessions.modifyUserSession
import tools.confido.application.sessions.transientUserData
import tools.confido.application.sessions.userSession
import tools.confido.refs.ref
import tools.confido.state.EmptyPV
import tools.confido.state.PresenterInfo
import tools.confido.state.PresenterView
import tools.confido.state.serverState
import tools.confido.utils.unixNow
import kotlin.time.Duration.Companion.seconds

fun presenterRoutes(routing: Routing) = routing.apply {
    webSocketST("/state/presenter/track") {
        RouteBody(call).withUser {
            transientData.activePresenterWindows += 1
            transientData.refreshSessionWebsockets()
            System.err.println("new presenter\n")
            try {
                timeoutMillis = 30*1000
                pingIntervalMillis = 15*1000

                incoming.receiveCatching()
            } finally {
                System.err.println("presenter died\n")
                transientData.activePresenterWindows -= 1
                transientData.refreshSessionWebsockets()
            }
        }
    }

    postST("/presenter/set") {
        withUser {
            val view = call.receive<PresenterView>()
            if (!view.isValid()) badRequest("View is not valid")
            call.modifyUserSession {
                val oldInfo = it.presenterInfo ?: PresenterInfo()
                val newInfo = oldInfo.copy(view = view, lastUpdate = unixNow())
                it.copy(presenterInfo = newInfo)
            }
            transientData.refreshSessionWebsockets()
            call.respond(HttpStatusCode.OK)
        }
    }
}
