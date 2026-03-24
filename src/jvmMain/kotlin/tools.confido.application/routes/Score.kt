package tools.confido.application.routes

import getScore
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import payloads.requests.CalibrationRequest
import payloads.responses.*
import rooms.Room
import rooms.RoomPermission
import rooms.effectiveQuestions
import tools.confido.application.UserPresentationContext
import tools.confido.application.calibration.getScoredPrediction
import tools.confido.application.getUserPresentation
import tools.confido.calibration.getCalibration
import tools.confido.calibration.sum
import tools.confido.distributions.BinaryDistribution
import tools.confido.question.QuestionState
import tools.confido.refs.Ref
import tools.confido.refs.eqid
import tools.confido.refs.ref
import rooms.effectiveMembers
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.BinaryValue
import tools.confido.state.serverState
import tools.confido.utils.mapDeref

import tools.confido.question.Question
import users.User

/** Compute the room scoreboard: list of [ScoreboardEntry] sorted descending by score. */
private suspend fun computeScoreboard(room: Room, observer: User): List<ScoreboardEntry> {
    val questionRefs = room.effectiveQuestions
        ?.filter { it.includedInScoring }
        ?.map { it.question }
        ?: room.questions
    val questions = questionRefs.mapDeref().filter { q ->
        q.answerSpace is BinarySpace && q.state == QuestionState.RESOLVED && q.resolution != null &&
                q.effectiveSchedule.score != null
    }
    val entries = serverState.users.mapNotNull { (_, u) ->
        val uScores = questions.mapNotNull { q -> getScore(q, getScoredPrediction(q, u.ref)) }
        if (uScores.isEmpty()) null
        else ScoreboardEntry(
            user = getUserPresentation(u, observer, UserPresentationContext.SCOREBOARD, room),
            score = uScores.sum(),
        )
    }
    return entries.sortedBy { -it.score }
}

fun scoreRoutes(routing: Routing) = routing.apply {

    getST("/scores/my") {
        call.withUser {
            // One entry per question: the primary room is where the question actually lives;
            // additional rooms are group rooms that aggregate it via effectiveQuestions.
            val primaryItems = linkedMapOf<String, MyScoreItem>() // questionId -> item
            val additionalRoomsMap = mutableMapOf<String, MutableList<Ref<Room>>>() // questionId -> group rooms

            // Show own scores for any room where scoring is enabled and the user is a member.
            // This does not require VIEW_SCORES (which controls the scoreboard visibility).
            val rooms = serverState.rooms.values.filter { room ->
                room.scoring != null &&
                    room.effectiveMembers.any { user eqid it.user }
            }

            for (room in rooms) {
                val effQuestions = room.effectiveQuestions
                val isAggregating = effQuestions != null
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
                    if (isAggregating) {
                        // This is a group room aggregating a child's question -- record as additional room
                        additionalRoomsMap.getOrPut(q.id) { mutableListOf() }.add(room.ref)
                        continue
                    }
                    if (q.id in primaryItems) continue // already seen from its primary room
                    val pred = getScoredPrediction(q, user.ref) ?: continue
                    val dist = pred.dist as? BinaryDistribution ?: continue
                    val score = getScore(q, pred) ?: continue
                    val resolveTime = q.stateHistory
                        .filter { it.newState == QuestionState.RESOLVED }
                        .maxByOrNull { it.at }?.at

                    primaryItems[q.id] = MyScoreItem(
                        questionRef = q.ref,
                        questionName = q.name,
                        roomRef = room.ref,
                        roomName = room.name,
                        score = score,
                        prediction = dist.yesProb,
                        resolution = (q.resolution as BinaryValue).value,
                        scoreTime = q.effectiveSchedule.score!!,
                        resolveTime = resolveTime,
                    )
                }
            }

            // Merge additional rooms into primary items
            val items = primaryItems.values.map { item ->
                val extras = additionalRoomsMap[item.questionRef.id]
                if (extras != null) item.copy(additionalRooms = extras) else item
            }

            // Compute per-room rank for every room where the user has scores.
            val roomRanks = mutableMapOf<String, MyRankResponse>()
            for (room in rooms) {
                val scoreboard = computeScoreboard(room, user)
                val myIdx = scoreboard.indexOfFirst { it.user.isMyself }
                if (myIdx >= 0) {
                    roomRanks[room.id] = MyRankResponse(
                        rank = myIdx + 1,
                        totalParticipants = scoreboard.size,
                        myScore = scoreboard[myIdx].score,
                    )
                }
            }

            call.respond(MyScoresResponse(
                items = items,
                totalScore = items.sumOf { it.score },
                roomRanks = roomRanks,
            ))
        }
    }

    getST("$roomUrl/scoreboard.api") {
        call.withUser {
            call.withRoom {
                assertPermission(RoomPermission.VIEW_SCORES, "Cannot view scoreboard.")
                val res = computeScoreboard(room, user)
                call.respond(res)
            }
        }
    }

    getST("$roomUrl/my_rank") {
        call.withUser {
            call.withRoom {
                // Only requires scoring to be enabled and user to be a room member;
                // does not require VIEW_SCORES (that controls the full scoreboard).
                if (room.scoring == null) notFound("Scoring is not enabled for this room.")
                if (room.userRole(user) == null) unauthorized("Not a member of this room.")
                val scoreboard = computeScoreboard(room, user)
                val myEntry = scoreboard.indexOfFirst { it.user.isMyself }
                if (myEntry < 0) {
                    call.respond(io.ktor.http.HttpStatusCode.NotFound, "No scores for you in this room.")
                } else {
                    call.respond(MyRankResponse(
                        rank = myEntry + 1,
                        totalParticipants = scoreboard.size,
                        myScore = scoreboard[myEntry].score,
                    ))
                }
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
