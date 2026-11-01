package tools.confido.application.routes

import tools.confido.api.Api
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tools.confido.payloads.requests.CalibrationRequest
import tools.confido.payloads.requests.Everyone
import tools.confido.payloads.requests.Myself
import tools.confido.payloads.responses.BinaryCalibrationQuestion
import tools.confido.payloads.responses.CalibrationQuestion
import tools.confido.payloads.responses.NumericCalibrationQuestion
import tools.confido.rooms.Room
import tools.confido.rooms.RoomPermission
import tools.confido.application.calibration.getScoredPrediction
import tools.confido.calibration.*
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.question.Question
import tools.confido.question.resolvedAttributionIn
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.BinaryValue
import tools.confido.spaces.NumericSpace
import tools.confido.spaces.NumericValue
import tools.confido.state.serverState
import tools.confido.utils.mapDeref
import tools.confido.users.User
import tools.confido.question.effectiveSchedule

fun calibrationRoutes(routing: Routing) = routing.apply {
    fun calibQuestions(req: CalibrationRequest, user: User, exposingPredictions: Boolean = false): List<Question> {

        fun canRoom(room: Room) = when (req.who) {
            Myself -> {
                room.hasPermission(user, RoomPermission.VIEW_QUESTIONS)
            }
            Everyone -> {
                room.hasPermission(user, RoomPermission.VIEW_GROUP_CALIBRATION)
                        && ((!exposingPredictions) || room.hasPermission(user, RoomPermission.VIEW_ALL_GROUP_PREDICTIONS))
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
                    // Group calibration reads (and calibrationDetail exposes) the scored
                    // group prediction — an aggregate, so the unattributed-level
                    // anonymity threshold applies. Own calibration is unrestricted.
                    && (req.who == Myself
                        || q.resolvedAttributionIn(room).aggregatesVisible(serverState.userPred[q.ref]?.size ?: 0))
                ) q
                else null
            }
            else null
        } }.flatten()
        return questions
    }

    suspend fun calibAddPredictions(questions: List<Question>, req: CalibrationRequest, user: User) =
         questions.mapNotNull { q ->
            val pred = getScoredPrediction(q, when (req.who) {
                Myself -> user.ref
                Everyone -> null
            }) ?: return@mapNotNull null
            if ((req.fromTime == null || pred.ts >= req.fromTime.epochSeconds)
                && (req.toTime == null || pred.ts <= req.toTime.epochSeconds)) {
                q to pred
            } else {
                null
            }
        }

    apiST(Api.calibration) { req ->
        call.withUser {
            val questions = calibQuestions(req, user)
            val qpred = calibAddPredictions(questions, req, user)
            //println("questions: ${questions.map{ listOf(it.id, it.name, getUserCalibration(it, user.ref))}}")
            qpred.map { (q,pred) -> getCalibration(q, pred) }.sum()
        }
    }
    apiST(Api.calibrationDetail) { req ->
        call.withUser {
            val questions = calibQuestions(req, user, exposingPredictions = true)
            val qpred = calibAddPredictions(questions, req, user)
            val ret = qpred.flatMap { (q,pred)->
                val dist = pred.dist
                val scoreTime = q.effectiveSchedule.score
                when (q.answerSpace) {
                    is BinarySpace -> {
                        if (dist !is BinaryDistribution) return@flatMap emptyList()
                        if (q.resolution !is BinaryValue) return@flatMap emptyList()
                        val p = dist.yesProb
                        if (p == 0.5) return@flatMap emptyList()
                        val confidence = if (p < 0.5) 1-p else p
                        val (bin,expectedOutcome) = CalibrationBin.find(p) ?: return@flatMap emptyList()
                        listOf(
                            BinaryCalibrationQuestion(
                            question = q.ref,
                            questionName = q.name,
                            room = serverState.questionRoom[q.ref],
                            scoredPrediction =  pred,
                            scoreTime = scoreTime,
                            confidence = confidence,
                            bin = bin,
                            expectedOutcome = expectedOutcome,
                            actualOutcome = q.resolution.value,
                        )
                        )
                    }
                    is NumericSpace -> {
                        if (dist !is ContinuousProbabilityDistribution) return@flatMap emptyList()
                        if (q.resolution !is NumericValue) return@flatMap emptyList()
                        numeric2binary(dist, q.resolution.value).map { (confidence, didHit)->
                            val (bin,_) = CalibrationBin.find(confidence) ?: return@flatMap emptyList()
                            NumericCalibrationQuestion(
                                question = q.ref,
                                questionName = q.name,
                                room = serverState.questionRoom[q.ref],
                                space = q.answerSpace,
                                scoredPrediction =  pred,
                                scoreTime = scoreTime,
                                confidence = confidence,
                                bin = bin,
                                confidenceInterval = dist.confidenceInterval(confidence),
                                resolution = q.resolution.value,
                            )
                        }
                    }
                }
            }
            ret
        }
    }
}
