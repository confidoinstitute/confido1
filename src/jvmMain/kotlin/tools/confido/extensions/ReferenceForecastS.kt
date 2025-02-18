package tools.confido.extensions

import extensions.ReferenceForecastExtension
import extensions.ReferenceForcastKey
import extensions.PredictionWithUser
import extensions.UserScore
import io.ktor.server.routing.*
import rooms.RoomPermission
import tools.confido.application.routes.*
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.get
import tools.confido.refs.*
import tools.confido.state.getenvBool
import tools.confido.state.globalState
import tools.confido.state.serverState
import users.User
import kotlin.math.pow

object ReferenceForecastSE : ServerExtension, ReferenceForecastExtension() {
    val referenceText = System.getenv("CONFIDO_REFERENCE_FORECAST_LABEL") ?: "Reference"
    val groupPredText = System.getenv("CONFIDO_GROUP_PRED_LABEL") ?: "Group prediction"
    private fun calculateScore(prediction: Double, reference: Double): Double {
        // 10 points if exactly match prediction
        // 0 point for a 50% deviation
        val rawError = (prediction - reference).pow(2)
        val maxRawError = 0.25
        val clampedError = rawError.coerceAtMost(maxRawError) / maxRawError
        return 10 * (1 - clampedError)
    }

    override fun initRoutes(r: Routing) {
        r.getWS("/api$questionUrl/ext/reference_forecast/predictions.ws") {
            withQuestion {
                assertPermission(RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS, "You cannot view individual predictions.")
                val predictions = mutableListOf<PredictionWithUser>()

                // Add reference forecast if it exists
                question.extensionData[ReferenceForcastKey]?.let { probability ->
                    predictions.add(PredictionWithUser(referenceText, probability, true))
                }

                // Add group prediction
                serverState.groupPred[question.ref]?.let { pred->
                    predictions.add(PredictionWithUser(groupPredText, (pred.dist as? BinaryDistribution)?.yesProb ?: return@let ))
                }

                // Add user predictions
                serverState.userPred[question.ref]?.forEach { (userRef, pred) ->
                    val user = userRef.deref() ?: return@forEach
                    val dist = pred.dist as? BinaryDistribution ?: return@forEach
                    predictions.add(PredictionWithUser(user.nick ?: "(Anonymous)", dist.yesProb))
                }

                // Return predictions sorted by probability
                predictions.sortedBy { it.probability }
            }
        }

        r.getWS("/api$roomUrl/ext/reference_forecast/scoreboard.ws") {
            withRoom {
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
                val userScores = mutableMapOf<Ref<User>, MutableList<Double>>()
                questionsWithRef.forEach { (question, reference) ->
                    serverState.userPred[question.ref]?.forEach { (userRef, pred) ->
                        val dist = pred.dist as? BinaryDistribution ?: return@forEach
                        userScores.getOrPut(userRef) { mutableListOf() }
                            .add(calculateScore(dist.yesProb, reference))
                    }
                }

                // Compute average scores and create response
                userScores.mapNotNull { (userRef, scores) ->
                    val user = userRef.deref() ?: return@mapNotNull null
                    UserScore(
                        nickname = user.nick ?: "(Anonymous)",
                        score = scores.average(),
                        numQuestions = scores.size
                    )
                }.sortedByDescending { it.score }
            }
        }
    }
}
