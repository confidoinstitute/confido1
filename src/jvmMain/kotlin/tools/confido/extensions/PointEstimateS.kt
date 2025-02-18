package tools.confido.extensions

import extensions.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import rooms.RoomPermission
import tools.confido.application.routes.*
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.*
import tools.confido.spaces.NumericSpace
import tools.confido.spaces.NumericValue
import tools.confido.state.serverState

object PointEstimateSE : ServerExtension, PointEstimateExtension() {
    val groupPredText = System.getenv("CONFIDO_GROUP_PRED_LABEL") ?: "Group estimate"
    val resolutionText = System.getenv("CONFIDO_RESOLUTION_LABEL") ?: "Resolution"

    override fun initRoutes(r: Routing) {
        r.getWS("/api$questionUrl/ext/point_estimate/predictions.ws") {
            withQuestion {
                assertPermission(RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS, "You cannot view individual predictions.")
                val predictions = mutableListOf<PointEstimateWithUser>()

                // Add resolution if visible
                if (question.resolutionVisible && question.resolution != null) {
                    val resolution = question.resolution as? NumericValue
                    if (resolution != null) {
                        predictions.add(PointEstimateWithUser(resolutionText, resolution.value, true))
                    }
                }

                // Add group prediction
                serverState.groupPred[question.ref]?.let { pred->
                    predictions.add(PointEstimateWithUser(groupPredText, (pred.dist as? MultiPointEstimateContinuousDistribution)?.mean ?: return@let, true))
                }

                // Add user predictions
                serverState.userPred[question.ref]?.forEach { (userRef, pred) ->
                    val user = userRef.deref() ?: return@forEach
                    val dist = pred.dist as? PointEstimateContinuousDistribution ?: return@forEach
                    predictions.add(PointEstimateWithUser(user.nick ?: "(Anonymous)", dist.value))
                }

                // Return predictions sorted by value
                predictions.sortedBy { it.value }
            }
        }
    }

    override fun calculateGroupPred(q: Question, preds: Collection<Prediction>): Prediction? {
        if (!q.extensionData[PointEstimateKey]) return null
        if (preds.isEmpty()) return null

        val estimates = preds.mapNotNull { p ->
            val dist = p.dist
            when (dist) {
                is PointEstimateContinuousDistribution -> WeightedPointEstimate(dist.value, 1.0)
                else -> null
            }
        }

        if (estimates.isEmpty()) return null

        val dist = MultiPointEstimateContinuousDistribution(
            space = q.answerSpace as NumericSpace,
            estimates = estimates
        )
        return Prediction(
            ts = preds.map{ it.ts }.maxOrNull() ?: Clock.System.now().toEpochMilliseconds().toInt(),
            question = q.ref,
            user = null,
            dist = dist
        )
    }
}
