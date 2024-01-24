import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.BinaryValue
import tools.confido.spaces.NumericSpace
import tools.confido.spaces.NumericValue
import tools.confido.utils.toInt
import kotlin.math.pow

fun getScore(q: Question, dist: ProbabilityDistribution?): Double? {
    dist ?: return null
    return when (q.answerSpace) {
        is BinarySpace -> {
            if (q.resolution !is BinaryValue) return null
            if (dist !is BinaryDistribution) return null
            val correct = q.resolution.value.toInt().toDouble()
            val pred = dist.yesProb
            val qdiff = (correct - pred).pow(2)
            val norm = (0.25 - qdiff) * 4 // normalize score to range -3..1, with 0 for p=0.5
            norm
        }
        is NumericSpace -> {
            return null
        }
    }
}
fun getScore(q: Question, p: Prediction?)  = getScore(q, p?.dist)
