package tools.confido.calibration

import kotlinx.serialization.Serializable
import tools.confido.payloads.responses.CalibrationQuestion
import tools.confido.distributions.BinaryDistribution
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.ProbabilityDistribution
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.spaces.BinarySpace
import tools.confido.spaces.BinaryValue
import tools.confido.spaces.NumericSpace
import tools.confido.spaces.NumericValue
import tools.confido.utils.List2
import tools.confido.utils.endpoints
import tools.confido.utils.mid
import tools.confido.utils.toFixed
import kotlin.math.sqrt

@Serializable
enum class CalibrationBin(val range: ClosedFloatingPointRange<Double>) {
    BIN_52(0.5..0.55),
    BIN_60(0.55..0.65),
    BIN_70(0.65..0.75),
    BIN_80(0.75..0.85),
    BIN_90(0.85..0.95),
    BIN_97(0.95..1.0);

    val mid get() = range.mid

    val formattedRange get() = range.endpoints.joinToString(" – ") { (100*it).toFixed(0)+"%" }

    companion object {
        fun find(p: Double): Pair<CalibrationBin, Boolean>? =
            if (p == 0.5) null
            else if (p < 0.5) find(1-p)!!.first to false
            else entries.first{ p in it.range } to true
    }
}

@Serializable
data class CalibrationEntry(
    val counts: List2<Int> = List2(0, 0),
) {
    constructor(correct: Boolean, cnt: Int = 1) : this(if (correct) List2(0,cnt) else List2(cnt, 0))
    constructor(cq: CalibrationQuestion) : this(cq.isCorrect)
    constructor(cqs: Iterable<CalibrationQuestion>) : this(List2(cqs.groupBy { it.isCorrect }.mapValues { it.value.size }, 0))
    val successRate get() = if (counts.sum() == 0) null else counts[true].toDouble() / total.toDouble()
    val total get() = counts.sum()
    /** Whether this bin has enough data for meaningful calibration results. */
    val enoughData get() = total >= MIN_PER_BIN
    operator fun plus(other: CalibrationEntry) = CalibrationEntry(counts.zip(other.counts) { a, b -> a+b })
    override fun toString() = counts.toString()

    companion object {
        /** Minimum number of predictions in a bin for results to be considered meaningful. */
        const val MIN_PER_BIN = 3
    }
}


@Serializable
class CalibrationVector(val data: Map<CalibrationBin, CalibrationEntry>):
    Map<CalibrationBin, CalibrationEntry> by (data.withDefault{ CalibrationEntry() }) {
    constructor() : this(emptyMap())
    constructor(prob: Double, resolution: Boolean) : this(CalibrationBin.find(prob)?.let {(bin, predictedAnswer)->
        mapOf(bin to CalibrationEntry(resolution == predictedAnswer))
    } ?: emptyMap())

    override operator  fun get(key: CalibrationBin) = data[key] ?: CalibrationEntry()

    operator fun plus(other: CalibrationVector) =
        CalibrationVector(CalibrationBin.entries.map { it to (this[it] + other[it]) }.toMap())

    /** Return a new vector excluding bins without [CalibrationEntry.enoughData]. */
    fun filtered() = CalibrationVector(data.filterValues { it.enoughData })

    override fun toString() = data.toString()

    companion object {
        fun fromNumeric(dist: ContinuousProbabilityDistribution, resolution: Double) =
            numeric2binary(dist, resolution).map { CalibrationVector(it.first, it.second) }.sum()
    }
}

fun numeric2binary(dist: ContinuousProbabilityDistribution, resolution: Double) =
    CalibrationBin.entries.map {
        //println("N2B ${it.mid} ${resolution } ${dist.confidenceInterval(it.mid)} ${resolution in dist.confidenceInterval(it.mid)}")
        it.mid to (resolution in dist.confidenceInterval(it.mid))
    }

fun Iterable<CalibrationVector>.sum() = reduceOrNull{ a,b -> a+b  } ?: CalibrationVector()

/**
 * Compute a calibration score from 0 (worst) to 100 (perfect calibration).
 *
 * Uses weighted root-mean-square calibration error (wRMSCE):
 * for each bin with data, the squared difference between actual
 * accuracy and the bin midpoint is weighted by the number of
 * predictions in that bin. The result is inverted and scaled to 0–100.
 *
 * Returns null if the total number of predictions is less than [minPredictions].
 */
fun CalibrationVector.calibrationScore(minPredictions: Int = 0): Double? {
    var weightedSqError = 0.0
    var totalN = 0
    for (bin in CalibrationBin.entries) {
        val entry = this[bin]
        if (!entry.enoughData) continue
        val accuracy = entry.successRate ?: continue
        val error = accuracy - bin.mid
        weightedSqError += entry.total * error * error
        totalN += entry.total
    }
    if (totalN < minPredictions) return null
    if (totalN == 0) return null
    val rmsce = sqrt(weightedSqError / totalN)
    // Max possible RMSCE is 0.5 (e.g. 100% confident and always wrong).
    // Invert and scale to 0–100, clamping to the valid range.
    return ((1.0 - rmsce / 0.5) * 100).coerceIn(0.0, 100.0)
}

fun getCalibration(q: Question, dist: ProbabilityDistribution): CalibrationVector {
    val E = CalibrationVector()
    if (q.resolution == null) return E
    return when (q.answerSpace) {
        is BinarySpace -> {
            if (q.resolution !is BinaryValue) return E
            if (dist !is BinaryDistribution) return E
            CalibrationVector(dist.yesProb, q.resolution.value)
        }
        is NumericSpace -> {
            if (dist !is ContinuousProbabilityDistribution) return E
            if (q.resolution !is NumericValue) return E
            CalibrationVector.fromNumeric(dist, q.resolution.value)
        }
    }
}
fun getCalibration(q: Question, p: Prediction)  = getCalibration(q, p.dist)
