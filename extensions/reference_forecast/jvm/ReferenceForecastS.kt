package tools.confido.extensions.reference_forecast
import tools.confido.extensions.ServerExtension

import tools.confido.extensions.reference_forecast.ReferenceForecastExtension
import tools.confido.extensions.reference_forecast.ReferenceForcastKey
import tools.confido.extensions.reference_forecast.UserScore
import tools.confido.extensions.shared.ValueWithUser
import io.ktor.server.routing.*
import tools.confido.rooms.Room
import tools.confido.rooms.RoomPermission
import tools.confido.application.PermClass
import tools.confido.application.routes.*
import tools.confido.extensions.without
import tools.confido.question.Question
import tools.confido.store.*
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.get
import tools.confido.refs.*
import tools.confido.state.getenvBool
import tools.confido.state.serverState
import tools.confido.users.User
import kotlin.math.pow

object ReferenceForecastSE : ServerExtension, ReferenceForecastExtension() {
    val groupPredText = System.getenv("CONFIDO_GROUP_PRED_LABEL") ?: "Group prediction"

    // The reference forecast is the scoring target; it is set and read only by
    // moderator surfaces (edit-question dialog, presenter scoreboard offer), so
    // strip it from the wire for non-MANAGE_QUESTIONS classes — participants could
    // otherwise read the value they are scored against out of the question payload.
    // Only strip when present: ExtensionData has reference equality, so an
    // unconditional copy would defeat the memo equality cutoff for every question.
    override fun censorQuestion(question: Question, room: Room, cls: PermClass): Question =
        if (RoomPermission.MANAGE_QUESTIONS in (cls.roomPerms[room.ref] ?: emptySet())
            || question.extensionData[ReferenceForcastKey] == null) question
        else question.copy(extensionData = question.extensionData.without(ReferenceForcastKey))
    private fun calculateScore(prediction: Double, reference: Double): Double {
        // 10 points if exactly match prediction
        // 0 point for a 50% deviation
        val rawError = (prediction - reference).pow(2)
        val maxRawError = 0.25
        val clampedError = rawError.coerceAtMost(maxRawError) / maxRawError
        return 10 * (1 - clampedError)
    }

    override fun initRoutes(r: Routing) {
        r.viewWS(referenceScoreboardTopic, gate = { rRef ->
            val rId = rRef.id
            combineViews(
                roomStore.state.asView().at(rId),
                questionStore.state.asView(),
                userStore.state.asView(),
                latestPredictions.state.asView(),
                predictionData.groupPredState.asView(),
            )
        }) { rRef, _ ->
            call.withRoom(rRef) {
                assertPermission(RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS, "You cannot view individual predictions.")

                // Find all questions with reference forecasts
                val questionsWithRef = room.questions.mapNotNull { qRef ->
                    qRef.deref()?.let { q ->
                        q.extensionData[ReferenceForcastKey]?.let { ref ->
                            q to ref
                        }
                    }
                }

                // Calculate scores for each user
                val userScores = mutableMapOf<Ref<User>?, MutableList<Double>>()
                questionsWithRef.forEach { (question, reference) ->
                    serverState.userPred[question.ref]?.forEach { (userRef, pred) ->
                        val dist = pred.dist as? BinaryDistribution ?: return@forEach

                        userScores.getOrPut(userRef) { mutableListOf() }
                            .add(calculateScore(dist.yesProb, reference))
                    }
                    (serverState.groupPred[question.ref]?.dist as? BinaryDistribution)?.let { gp->
                        userScores.getOrPut(null) { mutableListOf() }
                            .add(calculateScore(gp.yesProb, reference))
                    }
                }

                // Compute average scores and create response
                userScores.mapNotNull { (userRef, scores) ->
                    if (userRef == null)
                        UserScore(
                            "Crowd",
                            scores.sum(),
                            numQuestions = scores.size,
                            isSpecial = true
                        )
                    else {
                        val user = userRef.deref() ?: return@mapNotNull null
                        UserScore(
                            nickname = user.nick ?: "(Anonymous)",
                            score = scores.sum(),
                            numQuestions = scores.size
                        )
                    }
                }.sortedByDescending { it.score }
            }
        }
    }
}
