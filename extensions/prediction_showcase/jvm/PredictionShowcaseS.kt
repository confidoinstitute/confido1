package tools.confido.extensions.prediction_showcase
import tools.confido.extensions.get

import tools.confido.extensions.ServerExtension
import tools.confido.extensions.reference_forecast.ReferenceForcastKey
import tools.confido.extensions.shared.ValueWithUser
import io.ktor.server.routing.*
import tools.confido.rooms.AttributionLevel
import tools.confido.rooms.aliasFor
import tools.confido.rooms.RoomPermission
import tools.confido.question.effectiveAttribution
import tools.confido.question.room
import tools.confido.application.routes.*
import tools.confido.store.*
import tools.confido.distributions.*
import tools.confido.refs.*
import tools.confido.spaces.*
import tools.confido.state.serverState
import tools.confido.utils.capFirst

object PredictionShowcaseSE : ServerExtension, PredictionShowcaseExtension() {
    val resolutionText = System.getenv("CONFIDO_RESOLUTION_LABEL") ?: "Resolution"
    val referenceText = System.getenv("CONFIDO_REFERENCE_FORECAST_LABEL") ?: "Reference"

    override fun initRoutes(r: Routing) {
        r.viewWS(showcasePredictionsTopic, gate = { qRef ->
            val qId = qRef.id
            combineViews(
                questionStore.state.asView().at(qId),
                roomStore.state.asView(),
                userStore.state.asView(),
                latestPredictions.state.asView().at(qRef),
                predictionData.groupPredState.asView().at(qRef),
            )
        }) { qRef, _ ->
            call.withQuestion(qRef) {
                assertIndividualPredictionAccess("You cannot view individual predictions.")
                val predictions = mutableListOf<ValueWithUser>()

                // Add reference forecast for binary questions
                if (question.answerSpace is BinarySpace) {
                    question.extensionData[ReferenceForcastKey]?.let { probability ->
                        predictions.add(ValueWithUser(referenceText, probability, true))
                    }
                }

                // Add resolution if visible
                if (question.resolutionVisible && question.resolution != null) {
                    val resolution = question.resolution
                    when (resolution) {
                        is NumericValue -> predictions.add(ValueWithUser(resolutionText, resolution.value, true))
                        is BinaryValue -> predictions.add(ValueWithUser(resolutionText, if (resolution.value) 1.0 else 0.0, true))
                        else -> {} // Ignore other types for now
                    }
                }

                val groupPredText = "${question.groupTerminology.term.capFirst()} ${question.predictionTerminology.term}"
                // Add group prediction
                serverState.groupPred[question.ref]?.let { pred->
                    when (val dist = pred.dist) {
                        is ContinuousProbabilityDistribution -> predictions.add(ValueWithUser(groupPredText, dist.mean, true))
                        is BinaryDistribution -> predictions.add(ValueWithUser(groupPredText, dist.yesProb, true))
                        else -> {} // Ignore other types for now
                    }
                }

                // Add user predictions. The showcase is a presenter (audience-facing)
                // surface: masked attribution levels show the alias only, never a
                // pierced real name; unattributed levels serve nothing (the client
                // filters such questions out, this enforces it server-side).
                val level = question.effectiveAttribution.level
                val showcaseRoom = question.room
                if (level < AttributionLevel.ANONYMOUS)
                    serverState.userPred[question.ref]?.forEach { (userRef, pred) ->
                        val user = userRef.deref() ?: return@forEach
                        val name = if (level == AttributionLevel.PSEUDONYM)
                            showcaseRoom?.aliasFor(user.id) ?: "Anonymous participant"
                        else user.nick ?: "(Anonymous)"
                        when (val dist = pred.dist) {
                            is ContinuousProbabilityDistribution -> predictions.add(ValueWithUser(name, dist.mean, false))
                            is BinaryDistribution -> predictions.add(ValueWithUser(name, dist.yesProb, false))
                            else -> {} // Ignore other types for now
                        }
                    }

                // Return predictions sorted by value
                predictions.sortedByDescending { it.value }
            }
        }
    }
}
