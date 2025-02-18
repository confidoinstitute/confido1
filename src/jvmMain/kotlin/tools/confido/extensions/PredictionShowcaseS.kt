package tools.confido.extensions

import extensions.*
import extensions.shared.ValueWithUser
import io.ktor.server.routing.*
import rooms.RoomPermission
import tools.confido.application.routes.*
import tools.confido.distributions.*
import tools.confido.refs.*
import tools.confido.spaces.*
import tools.confido.state.serverState

object PredictionShowcaseSE : ServerExtension, PredictionShowcaseExtension() {
    val groupPredText = System.getenv("CONFIDO_GROUP_PRED_LABEL") ?: "Group prediction"
    val resolutionText = System.getenv("CONFIDO_RESOLUTION_LABEL") ?: "Resolution"

    override fun initRoutes(r: Routing) {
        r.getWS("/api$questionUrl/ext/prediction_showcase/predictions.ws") {
            withQuestion {
                assertPermission(RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS, "You cannot view individual predictions.")
                val predictions = mutableListOf<ValueWithUser>()

                // Add resolution if visible
                if (question.resolutionVisible && question.resolution != null) {
                    val resolution = question.resolution
                    when (resolution) {
                        is NumericValue -> predictions.add(ValueWithUser(resolutionText, resolution.value, true))
                        is BinaryValue -> predictions.add(ValueWithUser(resolutionText, if (resolution.value) 1.0 else 0.0, true))
                        else -> {} // Ignore other types for now
                    }
                }

                // Add group prediction
                serverState.groupPred[question.ref]?.let { pred->
                    when (val dist = pred.dist) {
                        is ContinuousProbabilityDistribution -> predictions.add(ValueWithUser(groupPredText, dist.mean, true))
                        is BinaryDistribution -> predictions.add(ValueWithUser(groupPredText, dist.yesProb, true))
                        else -> {} // Ignore other types for now
                    }
                }

                // Add user predictions
                serverState.userPred[question.ref]?.forEach { (userRef, pred) ->
                    val user = userRef.deref() ?: return@forEach
                    when (val dist = pred.dist) {
                        is PointEstimateContinuousDistribution -> predictions.add(ValueWithUser(user.nick ?: "(Anonymous)", dist.value, false))
                        is BinaryDistribution -> predictions.add(ValueWithUser(user.nick ?: "(Anonymous)", dist.yesProb, false))
                        else -> {} // Ignore other types for now
                    }
                }

                // Return predictions
                predictions
            }
        }
    }
}
