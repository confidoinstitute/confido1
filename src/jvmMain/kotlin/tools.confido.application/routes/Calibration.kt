package tools.confido.application.routes

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import payloads.requests.CalibrationRequest
import payloads.requests.Everyone
import payloads.requests.Myself
import rooms.Room
import rooms.RoomPermission
import tools.confido.application.calibration.getScoredPrediction
import tools.confido.calibration.CalibrationVector
import tools.confido.calibration.getCalibration
import tools.confido.calibration.sum
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace
import tools.confido.state.serverState
import tools.confido.utils.mapDeref

fun calibrationRoutes(routing: Routing) = routing.apply {

    postST("/calibration") {
        withUser {
            val req = call.receive<CalibrationRequest>()
            fun canRoom(room: Room) = when (req.who) {
                Myself -> {
                    room.hasPermission(user, RoomPermission.VIEW_QUESTIONS)
                }
                Everyone -> {
                    room.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS)
                }
                //is UserSet -> { // not implemented
                //    room.hasPermission(user, RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS)
                //}
            }
            val rooms = req.rooms?.mapDeref() ?: if (req.questions != null) serverState.rooms.values.filter{it.questions.toSet().intersect(req.questions).isNotEmpty()}
                else serverState.rooms.values.filter(::canRoom)
            if (!rooms.all(::canRoom)) unauthorized("Insufficient permissions for the specified rooms")
            val questions = rooms.map { room-> room.questions.mapNotNull{
                if ((req.questions == null || it in req.questions) && (it !in req.excludeQuestions)) {
                    val q = it.deref() ?: return@mapNotNull null
                    if (
                        q.resolution != null
                        && (q.resolutionVisible
                            || (req.includeHiddenResolutions && room.hasPermission(user, RoomPermission.VIEW_ALL_RESOLUTIONS)))
                        && (q.answerSpace is BinarySpace || req.includeNumeric)
                    ) q
                    else null
                }
                else null
            } }.flatten()
            //println("questions: ${questions.map{ listOf(it.id, it.name, getUserCalibration(it, user.ref))}}")
            val calib = questions.map { q ->
                val pred = getScoredPrediction(q, when (req.who) {
                    Myself -> user.ref
                    Everyone -> null
                }) ?: return@map CalibrationVector()
                if ((req.fromTime == null || pred.ts >= req.fromTime.epochSeconds)
                    && (req.toTime == null || pred.ts <= req.toTime.epochSeconds)) {
                    getCalibration(q, pred)
                } else {
                    CalibrationVector()
                }

            }.sum()
            call.respond(calib)
        }
    }
}
