package tools.confido.application.routes

import getScore
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import payloads.requests.CalibrationRequest
import payloads.responses.MyScoreItem
import payloads.responses.MyScoresResponse
import rooms.RoomPermission
import tools.confido.application.calibration.getScoredPrediction
import tools.confido.extensions.ServerExtension
import tools.confido.calibration.getCalibration
import tools.confido.calibration.sum
import tools.confido.distributions.BinaryDistribution
import tools.confido.question.QuestionState
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.BinaryValue
import tools.confido.state.serverState
import tools.confido.utils.mapDeref

fun scoreRoutes(routing: Routing) = routing.apply {

    getST("/scores/my") {
        call.withUser {
            val items = mutableListOf<MyScoreItem>()

            val rooms = serverState.rooms.values.filter { room ->
                room.hasPermission(user, RoomPermission.VIEW_SCORES)
            }

            for (room in rooms) {
                // Use effective questions (e.g. aggregated from child rooms) if provided by an extension,
                // filtering to those included in scoring. Otherwise use the room's own questions.
                val effQuestions = ServerExtension.enabled.firstNotNullOfOrNull {
                    it.effectiveQuestions(room)
                }
                val questionRefs = effQuestions
                    ?.filter { it.includedInScoring }
                    ?.map { it.question }
                    ?: room.questions
                val questions = questionRefs.mapDeref().filter { q ->
                    q.answerSpace is BinarySpace &&
                            q.state == QuestionState.RESOLVED &&
                            q.resolution is BinaryValue &&
                            (q.resolutionVisible || room.hasPermission(user, RoomPermission.VIEW_ALL_RESOLUTIONS)) &&
                            q.effectiveSchedule.score != null
                }
                for (q in questions) {
                    val pred = getScoredPrediction(q, user.ref) ?: continue
                    val dist = pred.dist as? BinaryDistribution ?: continue
                    val score = getScore(q, pred) ?: continue
                    val resolveTime = q.stateHistory
                        .filter { it.newState == QuestionState.RESOLVED }
                        .maxByOrNull { it.at }?.at

                    items.add(MyScoreItem(
                        questionRef = q.ref,
                        questionName = q.name,
                        roomRef = room.ref,
                        roomName = room.name,
                        score = score,
                        prediction = dist.yesProb,
                        resolution = (q.resolution as BinaryValue).value,
                        scoreTime = q.effectiveSchedule.score!!,
                        resolveTime = resolveTime,
                    ))
                }
            }

            call.respond(MyScoresResponse(
                items = items,
                totalScore = items.sumOf { it.score },
            ))
        }
    }

    getST("$roomUrl/scoreboard.api") {
        call.withUser {
            call.withRoom {
                assertPermission(RoomPermission.VIEW_SCORES, "Cannot view scoreboard.")
                // Use effective questions for scoring if provided by an extension.
                val effQuestions = ServerExtension.enabled.firstNotNullOfOrNull {
                    it.effectiveQuestions(room)
                }
                val questionRefs = effQuestions
                    ?.filter { it.includedInScoring }
                    ?.map { it.question }
                    ?: room.questions
                val questions = questionRefs.mapDeref().filter { q ->
                        q.answerSpace is BinarySpace && q.state == QuestionState.RESOLVED && q.resolution != null &&
                                q.effectiveSchedule.score != null
                    }
                val scores = serverState.users.mapNotNull { (_, u) ->
                    val uScores = questions.mapNotNull { q -> getScore(q, getScoredPrediction(q, u.ref)) }
                    if (uScores.isEmpty()) null
                    else u.nick to uScores.sum()
                }
                val res = scores.sortedBy { -it.second }
                call.respond(res)
            }
        }
    }

    getST("$questionUrl/my_score") {
        call.withUser {
            call.withQuestion {
                val pred = getScoredPrediction(question, user.ref)
                val resp = if (question.state != QuestionState.RESOLVED || question.resolution == null
                                || question.answerSpace !is BinarySpace || pred == null) {
                    null to null
                } else {
                    pred to getScore(question, pred)
                }
            }
        }
    }

}
