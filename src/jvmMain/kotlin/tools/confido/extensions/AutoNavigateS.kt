package tools.confido.extensions

import extensions.AutoNavigateExtension
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import tools.confido.application.routes.postST
import tools.confido.application.routes.webSocketST

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
}