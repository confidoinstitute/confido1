package tools.confido.calibration

import kotlinx.serialization.Serializable
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
import tools.confido.utils.mid

@Serializable
enum class CalibrationBin(val range: ClosedFloatingPointRange<Double>) {
    BIN_52(0.5..0.55),
    BIN_60(0.55..0.65),
    BIN_70(0.65..0.75),
    BIN_80(0.75..0.85),
    BIN_90(0.85..0.95),
    BIN_97(0.95..1.0);

    val mid get() = range.mid

    data class BinSpec(
        val bin: CalibrationBin,
        val weight: Double,
        val correctAnswer: Boolean,
    )
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
    val successRate get() = if (counts.sum() == 0) null else counts[true].toDouble() / counts.sum().toDouble()
    operator fun plus(other: CalibrationEntry) = CalibrationEntry(counts.zip(other.counts) { a, b -> a+b })
    override fun toString() = counts.toString()
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

fun Iterable<CalibrationVector>.sum() = reduce{ a,b -> a+b  }

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
