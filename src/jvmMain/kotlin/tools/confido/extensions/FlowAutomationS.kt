package tools.confido.extensions.flow_automation

import extensions.flow_automation.FlowAutomationExtension
import extensions.flow_automation.RoomFlowKey
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tools.confido.application.routes.postST
import tools.confido.application.routes.roomUrl
import tools.confido.application.routes.withRoom
import tools.confido.application.sessions.TransientData
import tools.confido.extensions.ServerExtension
import tools.confido.extensions.with
import tools.confido.state.serverState

object FlowAutomationSE: ServerExtension, FlowAutomationExtension() {
    override fun initRoutes(r: Routing) {
        r.postST("/api/$roomUrl/ext/flow_automation/flow") {
            val newFlow = call.receiveText().trim().ifEmpty { null }
            withRoom {
                serverState.roomManager.modifyEntity(room.id) {
                    it.copy(extensionData = it.extensionData.with(RoomFlowKey, newFlow))
               }
            }
            TransientData.refreshAllWebsockets()
            call.respond(HttpStatusCode.OK)
        }
    }
}