package tools.confido.extensions

import extensions.QuestionGroupsExtension
import extensions.QuestionGroupsKey
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import rooms.RoomPermission
import tools.confido.application.routes.badRequest
import tools.confido.application.routes.postST
import tools.confido.application.routes.roomUrl
import tools.confido.application.routes.withRoom
import tools.confido.application.sessions.TransientData
import tools.confido.question.QuestionState
import tools.confido.state.serverState
import tools.confido.utils.forEachDeref

object QuestionGroupsSE : ServerExtension, QuestionGroupsExtension() {
    override fun initRoutes(r: Routing) {
        println("INITROUTES:"+"/api$roomUrl/groups/{gid}/set_state")
        r.apply {
            postST("/api$roomUrl/groups/{gid}/set_state") {
                println("ROUTE")
                val gid = call.parameters["gid"]?.ifEmpty { null } ?: badRequest("missing group id")
                val newState = call.receive<QuestionState>()
                withRoom {
                    assertPermission(RoomPermission.MANAGE_QUESTIONS, "need moderator privilege to alter state")
                    serverState.withTransaction {
                        room.questions.forEachDeref { q ->
                            if (q.extensionData[QuestionGroupsKey].contains(gid))
                                serverState.questionManager.modifyEntity(q.id) { it.withState(newState) }
                        }
                    }

                    TransientData.refreshAllWebsockets()
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}


