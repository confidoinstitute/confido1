package tools.confido.extensions

import extensions.ReferenceForecastExtension
import extensions.ReferenceForcastKey
import extensions.PredictionWithUser
import io.ktor.server.routing.*
import rooms.RoomPermission
import tools.confido.application.routes.*
import tools.confido.distributions.BinaryDistribution
import tools.confido.extensions.get
import tools.confido.refs.*
import tools.confido.state.serverState

object ReferenceForecastSE : ServerExtension, ReferenceForecastExtension() {
    override fun initRoutes(r: Routing) {
        r.getWS("/api$questionUrl/ext/reference_forecast/predictions.ws") {
            withQuestion {
                assertPermission(RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS, "You cannot view individual predictions.")
                val predictions = mutableListOf<PredictionWithUser>()

                // Add reference forecast if it exists
                question.extensionData[ReferenceForcastKey]?.let { probability ->
                    predictions.add(PredictionWithUser("Reference", probability, true))
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
    }
}
