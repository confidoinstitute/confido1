package extensions.auto_navigate
import tools.confido.extensions.ServerExtension

import extensions.auto_navigate.AutoNavigateExtension
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import rooms.Room
import tools.confido.application.routes.postST
import tools.confido.application.routes.webSocketST
import tools.confido.state.AutoNavigateAction
import tools.confido.state.FlowAction

object AutoNavigateSE: ServerExtension, AutoNavigateExtension() {
    val navFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override fun initRoutes(r: Routing) {
        r.apply {
            webSocketST("/api/ext/auto_navigate/follow.ws") {
                navFlow.collect {
                    outgoing.send(Frame.Text(it))
                }
            }
            postST("/api/ext/auto_navigate/navigate") {
                navFlow.emit(call.receiveText())
                call.respond(HttpStatusCode.OK)
            }
        }
    }

    override suspend fun executeFlowAction(action: FlowAction, room: Room): Boolean {
        if (action !is AutoNavigateAction) return false
        navFlow.emit(action.url)
        return true
    }
}