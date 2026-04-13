package extensions.update_scatter_plot

import tools.confido.extensions.ServerExtension
import tools.confido.extensions.get
import tools.confido.extensions.with
import tools.confido.extensions.modify
import io.ktor.http.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import rooms.Room
import rooms.RoomPermission
import tools.confido.application.routes.*
import tools.confido.application.sessions.TransientData
import tools.confido.refs.ref
import tools.confido.state.FlowAction
import tools.confido.state.serverState
import tools.confido.utils.List2
import tools.confido.refs.*
import tools.confido.utils.generateId
import tools.confido.utils.unixNow

object UpdateScatterPlotSE: ServerExtension, UpdateScatterPlotExt {

    /**
     * Resolve a [TimepointReference] to an [Instant] for the update plot query.
     * Returns null to indicate "use latest predictions" (maps to DISTANT_FUTURE in the query logic).
     */
    private fun resolveTimepoint(tp: TimepointReference, room: Room): Instant? = when (tp) {
        is NowTimepoint -> null
        is TimestampTimepoint -> tp.time
        is SnapshotTimepoint -> {
            val snapshots = if (tp.question != null) {
                serverState.questions[tp.question.id]?.extensionData?.get(SnapshotsKey) ?: emptyList()
            } else {
                room.extensionData[SnapshotsKey]
            }
            snapshots.find { it.title == tp.name }?.let { Instant.fromEpochSeconds(it.ts.toLong()) }
        }
    }

    override fun initRoutes(r: Routing) {
        r.apply {
            postST("/api$questionUrl/snapshots/add") {
                call.withUser {
                    call.withQuestion {
                        val newId= generateId()
                        val snap = call.receive<Snapshot>().copy(owner = user.ref, id= newId)
                        assertPermission(RoomPermission.VIEW_QUESTIONS, "no room access")
                        if (snap.public) assertPermission(RoomPermission.MANAGE_QUESTIONS, "not enough permissions to create public snapshot")
                        serverState.questionManager.modifyEntity(question.id) { it.copy(extensionData = it.extensionData.with(
                            SnapshotsKey, (it.extensionData[SnapshotsKey] + listOf(snap)).sortedBy { it.ts })) }
                        call.respond(newId)
                        TransientData.refreshAllWebsockets()
                    }
                }
            }
            deleteST("/api$questionUrl/snapshots/{snapID}") {
                call.withUser {
                    call.withQuestion {
                        val snap = question.extensionData[SnapshotsKey].firstOrNull {
                            it.id == call.parameters["snapID"]
                        } ?: notFound("snapshot not found")
                        if (!(snap.owner == user.ref || snap.public && room.hasPermission(user, RoomPermission.MANAGE_QUESTIONS))) {
                            forbidden("You can only delete your own snapshots and moderators can delete public snapshots")
                        }
                        serverState.questionManager.modifyEntity(question.id, { it.copy(extensionData = it.extensionData.modify(SnapshotsKey) { it.filter { otherSnap -> otherSnap != snap } }) })
                        TransientData.refreshAllWebsockets()
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
            postST("/api$roomUrl/snapshots/add") {
                call.withUser {
                    call.withRoom {
                        val snap = call.receive<Snapshot>().copy(owner = user.ref)
                        assertPermission(RoomPermission.VIEW_QUESTIONS, "no room access")
                        if (snap.public) assertPermission(RoomPermission.MANAGE_QUESTIONS, "not enough permissions to create public snapshot")
                        serverState.roomManager.modifyEntity(room.id) { it.copy(extensionData = it.extensionData.with(
                            SnapshotsKey, (it.extensionData[SnapshotsKey] + listOf(snap)).sortedBy { it.ts })) }
                        TransientData.refreshAllWebsockets()
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
            deleteST("/api$roomUrl/snapshots/{snapID}") {
                call.withUser {
                    call.withRoom {
                        val snap = room.extensionData[SnapshotsKey].firstOrNull {
                            it.id == call.parameters["snapID"]
                        } ?: notFound("snapshot not found")
                        if (!(snap.owner == user.ref || snap.public && room.hasPermission(user, RoomPermission.MANAGE_QUESTIONS))) {
                            forbidden("You can only delete your own snapshots and moderators can delete public snapshots")
                        }
                        serverState.roomManager.modifyEntity(room.id, { it.copy(extensionData = it.extensionData.modify(SnapshotsKey) { it.filter { otherSnap -> otherSnap != snap } }) })
                        TransientData.refreshAllWebsockets()
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
            // Update plot: POST with TimepointReference-based request
            postST("/api$questionUrl/update_plot") {
                call.withQuestion {
                    assertPermission(
                        RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS,
                        "need permission to view individual predictions"
                    )
                    val req = call.receive<UpdatePlotRequest>()
                    val referenceQ = req.referenceQuestion ?: question.extensionData[UpdateReferenceQuestionKey]
                    val qs = List2(referenceQ ?: question.ref, question.ref)
                    val times = List2(req.timepoint1, req.timepoint2).map {
                        resolveTimepoint(it, room) ?: Instant.DISTANT_FUTURE
                    }
                    val users = serverState.userPred[question.ref]?.keys ?: emptySet()
                    val preds = users.mapNotNull { u->
                        val r = times.zip(qs) { t,q-> serverState.userPredHistManager.at(q, u, t) }
                        if (r == List2(null,null)) null else r
                    }
                    call.respond(preds)
                }
            }
        }
    }

    override suspend fun executeFlowAction(action: FlowAction, room: Room): Boolean {
        if (action !is CreateSnapshotAction) return false
        if (action.name.isBlank()) return true // silently skip empty-named snapshots

        val snapshot = Snapshot(
            ts = unixNow(),
            title = action.name,
            owner = null,
            public = true,
            id = generateId(),
        )

        if (action.question != null) {
            // Question-scoped snapshot: overwrite by name
            serverState.questionManager.modifyEntity(action.question.id) { q ->
                val existing = q.extensionData[SnapshotsKey]
                val updated = (existing.filter { it.title != action.name } + snapshot).sortedBy { it.ts }
                q.copy(extensionData = q.extensionData.with(SnapshotsKey, updated))
            }
        } else {
            // Room-wide snapshot: overwrite by name
            serverState.roomManager.modifyEntity(room.id) { r ->
                val existing = r.extensionData[SnapshotsKey]
                val updated = (existing.filter { it.title != action.name } + snapshot).sortedBy { it.ts }
                r.copy(extensionData = r.extensionData.with(SnapshotsKey, updated))
            }
        }
        return true
    }
}
