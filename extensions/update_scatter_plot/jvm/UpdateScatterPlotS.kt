package tools.confido.extensions.update_scatter_plot

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
import tools.confido.rooms.Room
import tools.confido.rooms.RoomPermission
import tools.confido.rooms.TimelineMark
import tools.confido.application.getUserPresentation
import tools.confido.application.UserPresentationContext
import tools.confido.application.routes.*
import tools.confido.application.sessions.TransientData
import tools.confido.refs.ref
import tools.confido.state.FlowAction
import tools.confido.state.serverState
import tools.confido.store.userPredictionLog
import tools.confido.utils.List2
import tools.confido.refs.*
import tools.confido.utils.generateId
import tools.confido.utils.unixNow

object UpdateScatterPlotSE: ServerExtension, UpdateScatterPlotExt {

    /**
     * Resolve a [TimepointReference] to an [Instant] for the update plot query.
     * Returns null to indicate "use latest predictions".
     */
    private fun resolveTimepoint(tp: TimepointReference, room: Room): Instant? = when (tp) {
        is NowTimepoint -> null
        is TimestampTimepoint -> tp.time
        is SnapshotTimepoint -> {
            // Core timeline marks first (public snapshots migrated there), then the
            // extension's remaining private snapshots.
            val question = tp.question?.let { serverState.questions[it.id] }
            val marks = if (tp.question != null) question?.timelineMarks ?: emptyList() else room.timelineMarks
            val snapshots = if (tp.question != null) {
                question?.extensionData?.get(SnapshotsKey) ?: emptyList()
            } else {
                room.extensionData[SnapshotsKey]
            }
            (marks.find { it.name == tp.name }?.ts ?: snapshots.find { it.title == tp.name }?.ts)
                ?.let { Instant.fromEpochSeconds(it.toLong()) }
        }
    }

    override fun initRoutes(r: Routing) {
        r.apply {
            postST("/api$questionUrl/snapshots/add") {
                call.withUser {
                    call.withQuestion {
                        val newId= generateId()
                        val snap = call.receive<Snapshot>().copy(owner = user.ref, id= newId)
                        assertVisible()
                        assertPermission(RoomPermission.VIEW_QUESTIONS, "no room access")
                        if (snap.public) {
                            // Public snapshots are core timeline marks now.
                            assertPermission(RoomPermission.MANAGE_QUESTIONS, "not enough permissions to create public snapshot")
                            val mark = TimelineMark(id = newId, ts = snap.ts, name = snap.title, owner = user.ref)
                            tools.confido.store.questionStore.modify(question.id) {
                                it.copy(timelineMarks = (it.timelineMarks + mark).sortedBy { m -> m.ts }) }
                        } else {
                            tools.confido.store.questionStore.modify(question.id) { it.copy(extensionData = it.extensionData.with(
                                SnapshotsKey, (it.extensionData[SnapshotsKey] + listOf(snap)).sortedBy { it.ts })) }
                        }
                        call.respond(newId)
                    }
                }
            }
            deleteST("/api$questionUrl/snapshots/{snapID}") {
                call.withUser {
                    call.withQuestion {
                        assertVisible()
                        val snapId = call.parameters["snapID"]
                        val mark = question.timelineMarks.firstOrNull { it.id == snapId }
                        if (mark != null) {
                            // Core timeline mark (public snapshot).
                            if (!room.hasPermission(user, RoomPermission.MANAGE_QUESTIONS))
                                forbidden("Only moderators can delete public snapshots")
                            tools.confido.store.questionStore.modify(question.id) {
                                it.copy(timelineMarks = it.timelineMarks.filter { m -> m.id != snapId }) }
                            call.respond(HttpStatusCode.OK)
                            return@withQuestion
                        }
                        val snap = question.extensionData[SnapshotsKey].firstOrNull {
                            it.id == snapId
                        } ?: notFound("snapshot not found")
                        if (!(snap.owner == user.ref || snap.public && room.hasPermission(user, RoomPermission.MANAGE_QUESTIONS))) {
                            forbidden("You can only delete your own snapshots and moderators can delete public snapshots")
                        }
                        tools.confido.store.questionStore.modify(question.id, { it.copy(extensionData = it.extensionData.modify(SnapshotsKey) { it.filter { otherSnap -> otherSnap != snap } }) })
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
            postST("/api$roomUrl/snapshots/add") {
                call.withUser {
                    call.withRoom {
                        val snap = call.receive<Snapshot>().copy(owner = user.ref)
                        assertPermission(RoomPermission.VIEW_QUESTIONS, "no room access")
                        if (snap.public) {
                            // Public snapshots are core timeline marks now.
                            assertPermission(RoomPermission.MANAGE_QUESTIONS, "not enough permissions to create public snapshot")
                            val mark = TimelineMark(id = generateId(), ts = snap.ts, name = snap.title, owner = user.ref)
                            tools.confido.store.roomStore.modify(room.id) {
                                it.copy(timelineMarks = (it.timelineMarks + mark).sortedBy { m -> m.ts }) }
                        } else {
                            tools.confido.store.roomStore.modify(room.id) { it.copy(extensionData = it.extensionData.with(
                                SnapshotsKey, (it.extensionData[SnapshotsKey] + listOf(snap)).sortedBy { it.ts })) }
                        }
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
            deleteST("/api$roomUrl/snapshots/{snapID}") {
                call.withUser {
                    call.withRoom {
                        val snapId = call.parameters["snapID"]
                        val mark = room.timelineMarks.firstOrNull { it.id == snapId }
                        if (mark != null) {
                            // Core timeline mark (public snapshot).
                            if (!room.hasPermission(user, RoomPermission.MANAGE_QUESTIONS))
                                forbidden("Only moderators can delete public snapshots")
                            tools.confido.store.roomStore.modify(room.id) {
                                it.copy(timelineMarks = it.timelineMarks.filter { m -> m.id != snapId }) }
                            call.respond(HttpStatusCode.OK)
                            return@withRoom
                        }
                        val snap = room.extensionData[SnapshotsKey].firstOrNull {
                            it.id == snapId
                        } ?: notFound("snapshot not found")
                        if (!(snap.owner == user.ref || snap.public && room.hasPermission(user, RoomPermission.MANAGE_QUESTIONS))) {
                            forbidden("You can only delete your own snapshots and moderators can delete public snapshots")
                        }
                        tools.confido.store.roomStore.modify(room.id, { it.copy(extensionData = it.extensionData.modify(SnapshotsKey) { it.filter { otherSnap -> otherSnap != snap } }) })
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
            // Update plot: POST with TimepointReference-based request
            postST("/api$questionUrl/update_plot") {
                call.withQuestion {
                    assertVisible()
                    assertPermission(
                        RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS,
                        "need permission to view individual predictions"
                    )
                    val req = call.receive<UpdatePlotRequest>()
                    val referenceQ = req.referenceQuestion ?: question.extensionData[UpdateReferenceQuestionKey]
                    val qs = List2(referenceQ ?: question.ref, question.ref)
                    val times = List2(req.timepoint1, req.timepoint2).map {
                        resolveTimepoint(it, room) // null = "use latest predictions"
                    }
                    val users = serverState.userPred[question.ref]?.keys ?: emptySet()
                    val rows = users.mapNotNull { u->
                        val r = times.zip(qs) { t,q-> userPredictionLog.at(q, u, t) }
                        if (r == List2(null,null)) null
                        else UpdatePlotRow(r, getUserPresentation(
                            u.deref(), user, UserPresentationContext.INDIVIDUAL_PREDICTION, room))
                    }
                    call.respond(rows)
                }
            }
        }
    }

    override suspend fun executeFlowAction(action: FlowAction, room: Room): Boolean {
        if (action !is CreateSnapshotAction) return false
        if (action.name.isBlank()) return true // silently skip empty-named snapshots

        // Snapshots created from presenter flows are public, i.e. core timeline
        // marks; overwrite-by-name semantics kept from the extension days.
        val mark = TimelineMark(id = generateId(), ts = unixNow(), name = action.name, owner = null)

        if (action.question != null) {
            tools.confido.store.questionStore.modify(action.question.id) { q ->
                q.copy(timelineMarks = (q.timelineMarks.filter { it.name != action.name } + mark).sortedBy { it.ts })
            }
        } else {
            tools.confido.store.roomStore.modify(room.id) { r ->
                r.copy(timelineMarks = (r.timelineMarks.filter { it.name != action.name } + mark).sortedBy { it.ts })
            }
        }
        return true
    }
}
