package tools.confido.application.routes

import getScore
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import payloads.requests.CalibrationRequest
import tools.confido.application.calibration.getScoredPrediction
import tools.confido.calibration.getCalibration
import tools.confido.calibration.sum
import tools.confido.question.QuestionState
import tools.confido.refs.ref
import tools.confido.spaces.BinarySpace
import tools.confido.state.serverState
import tools.confido.utils.mapDeref

fun scoreRoutes(routing: Routing) = routing.apply {

    getST("$roomUrl/scoreboard.api") {
        withUser {
            withRoom {
                val questions = room.questions.mapDeref().filter { q ->
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
        withUser {
            withQuestion {
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
