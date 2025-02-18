package tools.confido.extensions

import extensions.*
import kotlinx.datetime.Clock
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.ref
import tools.confido.spaces.NumericSpace

object PointEstimateSE : ServerExtension, PointEstimateExtension() {
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
