package tools.confido.distributions
import kotlinx.serialization.*
import kotlinx.serialization.modules.*
import tools.confido.spaces.*
import tools.confido.utils.*
import kotlin.math.*
import kotlin.sequences.*

interface ProbabilityDistribution {
    val space: Space
}


@Serializable
data class BinaryDistribution( // TODO: Make this a special case of a general ChoiceDistribution
    val yesProb: Double
) : ProbabilityDistribution {
    init { require(yesProb in 0.0..1.0) }
    override val space: BinarySpace get() = BinarySpace

    val noProb get() = 1 - yesProb

    fun probabilityOf(v: Boolean) = if (v) yesProb else noProb
}

interface ContinuousProbabilityDistribution : ProbabilityDistribution {
    override val space: NumericSpace

    fun pdf(x: Double): Double
    fun cdf(x: Double): Double
    fun icdf(p: Double): Double

    fun probabilityBetween(start: Double, end: Double) = cdf(end) - cdf(start)
    fun probabilityBetween(range: ClosedRange<Double>) = probabilityBetween(range.start, range.endInclusive)
    fun probabilityBetween(range: OpenEndRange<Double>) = probabilityBetween(range.start, range.endExclusive)
    fun densityBetween(start: Double, end: Double) = probabilityBetween(start, end) / (end - start)

    val mean: Double
    val stdev: Double
    val median get() = icdf(0.5)
    val preferredCICenter: Double
        get() = mean
    fun confidenceInterval(p: Double, preferredCenter: Double = preferredCICenter): ClosedRange<Double> {
        val probRadius = p / 2
        val centerCDF = cdf(preferredCenter)
        val leftCDF = centerCDF - probRadius
        val rightCDF = centerCDF + probRadius
        if (leftCDF <= 0) return space.min .. icdf(p)
        else if (rightCDF >= 1) return icdf(1-p) .. space.max
        else return icdf(leftCDF)..icdf(rightCDF)
    }

    fun discretize(binner: Binner) =
        DiscretizedContinuousDistribution(
            space,
            binner.binRanges.map { probabilityBetween(it) }.toList(),
            origMean = this.mean, origStdev = this.stdev)
    fun discretize(bins: Int = space.bins) = discretize(Binner(space, bins))
}

interface DiscretizedProbabilityDistribution : ProbabilityDistribution {
    val binProbs : List<Double>
}


@Serializable
data class DiscretizedContinuousDistribution(
    override val space: NumericSpace,
    override val binProbs: List<Double>,
    val origMean: Double?,
    val origStdev: Double?,
) : DiscretizedProbabilityDistribution, ContinuousProbabilityDistribution {

    @Transient
    val bins  = binProbs.size

    @Transient
    val binner = Binner(space, binProbs.size)

    @Transient
    val probBeforeBin by lazy {
        // force the last item to be exactly 1 to circumvent floating point roundoff errors
        binProbs.runningFoldIndexed(0.0, { idx, acc, d ->  if (idx==bins-1) 1.0 else acc+d })
    }

    @Transient
    val discretizedMean: Double by lazy {
        binProbs.zip(binner.binMidpoints) { p, midp -> p*midp }.sum()
    }

    @Transient
    val discretizedStdev: Double by lazy {
        sqrt(binProbs.zip(binner.binMidpoints) { p, midp -> p * (midp - discretizedMean).pow(2) }.sum())
    }

    override val mean: Double
        get() = origMean ?: discretizedMean
    override val stdev: Double
        get() = origStdev ?: discretizedStdev

    override fun pdf(x: Double): Double {
        return binProbs[binner.value2bin(x) ?: return 0.0] / binner.binSize
    }

    override fun cdf(x: Double): Double {
        if (x >= space.max) return 1.0
        val bin = binner.value2bin(x) ?: return 0.0
        return probBeforeBin[bin] + binProbs[bin] * (x - binner.binRanges[bin].start) / binner.binSize
    }

    override fun icdf(p: Double): Double {
        val idx = probBeforeBin.binarySearch(p)
        if (idx >= 0) {
            // we hit an exact bin boundary
            if (idx >= binner.bins) return space.max
            else return binner.binRanges[idx].start
        } else {
            val insertionPoint = -(idx + 1)
            check(insertionPoint > 0)
            val bin = insertionPoint - 1
            check(bin in 0 .. binner.bins)
            if (bin == binner.bins) return space.max
            val remainingProb = (p - probBeforeBin[bin]).clamp01()
            check(remainingProb in 0.0..1.0)
            val off = binner.binSize / binProbs[bin] * remainingProb
            return binner.binRanges[bin].start + off
        }
    }
}

@Serializable
object CanonicalNormalDistribution : ContinuousProbabilityDistribution {
    override val mean = 0.0
    override val stdev = 1.0
    override val space = NumericSpace()
    override fun pdf(x: Double) = exp(-(ln(2 * PI) + x * x) * 0.5)

    /*
     * Zelen, Marvin; Severo, Norman C. (1964).
     * Probability Functions (chapter 26).
     * Handbook of mathematical functions with formulas, graphs, and mathematical tables,
     * by Abramowitz, M.; and Stegun, I. A.:
     * National Bureau of Standards. New York, NY: Dover. ISBN 978-0-486-61272-0.
     */
    override fun cdf(x: Double): Double {
        val b0 = 0.2316419
        val b1 = 0.319381530
        val b2 = -0.356563782
        val b3 = 1.781477937
        val b4 = -1.821255978
        val b5 = 1.330274429

        if (x > 6) return 1.0
        if (x < -6) return 0.0

        val t = 1 / (1 + b0 * abs(x))
        val y = t * (b1 + b2 * t + (b3 + b4 * t + b5 * t * t) * t * t)
        return if (x < 0) pdf(-x) * y else 1 - pdf(x) * y
    }

    /*
     * Acklam, Peter John (2000).
     * http://web.archive.org/web/20151030215612/http://home.online.no/~pjacklam/notes/invnorm/
     */
    override fun icdf(p: Double): Double {
        if (p >= 1) return space.max
        if (p <= 0) return space.min

        val a0 = -3.969683028665376e+01
        val a1 = 2.209460984245205e+02
        val a2 = -2.759285104469687e+02
        val a3 = 1.383577518672690e+02
        val a4 = -3.066479806614716e+01
        val a5 = 2.506628277459239e+00

        val b0 = -5.447609879822406e+01
        val b1 = 1.615858368580409e+02
        val b2 = -1.556989798598866e+02
        val b3 = 6.680131188771972e+01
        val b4 = -1.328068155288572e+01

        val c0 = -7.784894002430293e-03
        val c1 = -3.223964580411365e-01
        val c2 = -2.400758277161838e+00
        val c3 = -2.549732539343734e+00
        val c4 = 4.374664141464968e+00
        val c5 = 2.938163982698783e+00

        val d0 = 7.784695709041462e-03
        val d1 = 3.224671290700398e-01
        val d2 = 2.445134137142996e+00
        val d3 = 3.754408661907416e+00

        val pL = 0.02425
        val pH = 1 - pL

        fun tail(q: Double) = (((((c0*q + c1)*q + c2)*q + c3)*q + c4)*q + c5) /
                               ((((d0*q + d1)*q + d2)*q + d3)*q + 1)

        if(p == 0.0) return Double.NEGATIVE_INFINITY
        if(p == 1.0) return Double.POSITIVE_INFINITY
        if (p < pL) return tail(sqrt(-ln(p) * 2))
        if (p > pH) return -tail(sqrt(-ln(1 - p) * 2))

        val q = p - 0.5
        val r = q*q
        return (((((a0*r + a1)*r + a2)*r + a3)*r + a4)*r + a5) * q /
               (((((b0*r + b1)*r + b2)*r + b3)*r + b4)*r + 1)

    }

}
interface TransformedDistribution : ContinuousProbabilityDistribution {
    val dist: ContinuousProbabilityDistribution
    val shift: Double
    val scale: Double

    val origSpace get() = space.copy(min = toOrig(space.min), max = toOrig(space.max))
    fun toOrig(x: Double) = (x - shift) / scale
    fun toOur(x: Double) = x * scale + shift

    override fun pdf(x: Double) = dist.pdf(toOrig(x))
    override fun cdf(x: Double) = dist.cdf(toOrig(x))
    override fun icdf(p: Double) : Double {
        if (p >= 1) return space.max
        if (p <= 0) return space.min
        return toOur(dist.icdf(p))
    }

    override val mean: Double get() = toOur(dist.mean)
    override val stdev: Double get() = dist.stdev * scale
}

@Serializable
data class NormalDistribution(override val mean: Double, override val stdev: Double) : TransformedDistribution {
    @Transient
    override val space = NumericSpace()
    override val dist get() = CanonicalNormalDistribution
    override val shift get() = mean
    override val scale get() = stdev
}

sealed class TruncatedDistribution : ContinuousProbabilityDistribution {
    abstract  val dist : ContinuousProbabilityDistribution

    @Transient
    val pIn by lazy { dist.probabilityBetween(space.min, space.max) }
    @Transient
    val pLT by lazy { dist.cdf(space.min) }

    override fun pdf(x: Double): Double {
        if (pIn == 0.0) return 0.0
        if (x < space.min) return 0.0
        if (x > space.max) return 0.0
        return dist.pdf(x) / pIn
    }

    override fun cdf(x: Double): Double {
        if (pIn == 0.0) return 0.0
        if (x <= space.min) return 0.0
        if (x >= space.max) return 1.0
        return dist.probabilityBetween(space.min, x) / pIn
    }

    override fun icdf(p: Double): Double = when(p) {
        0.0 -> space.min
        1.0 -> space.max
        else -> dist.icdf(p * pIn + pLT)
    }
}

@Serializable
data class TruncatedCanonicalNormalDistribution(
    override val space: NumericSpace,
) : TruncatedDistribution() {
    override val preferredCICenter: Double
        get() = 0.0
    override val dist get() = CanonicalNormalDistribution

    protected val a get() = space.min
    protected val b get() = space.max

    // https://github.com/toshas/torch_truncnorm/blob/890410b5dc22df3e1a5512ba3884e1edd8afb1e9/TruncatedNormal.py
    // https://people.sc.fsu.edu/~jburkardt/presentations/truncated_normal.pdf
    override val mean: Double
        get() = (dist.pdf(a) - dist.pdf(b)) / pIn

    override val stdev: Double
        get() = sqrt(1 - (b*dist.pdf(b) - a*dist.pdf(a)) / pIn - ((dist.pdf(b) - dist.pdf(a)) / pIn).pow(2))
}

@Serializable
data class TruncatedNormalDistribution(
    override val space: NumericSpace,
    val pseudoMean: Double,
    val pseudoStdev: Double,
) : TransformedDistribution {
    @Transient
    override val dist: ContinuousProbabilityDistribution = TruncatedCanonicalNormalDistribution(origSpace)
    override val shift get() = pseudoMean
    override val scale get() = pseudoStdev

}

val distributionsSM = SerializersModule {
    polymorphic(ProbabilityDistribution::class) {
        subclass(BinaryDistribution::class)
        subclass(DiscretizedContinuousDistribution::class)
        subclass(TruncatedNormalDistribution::class)
        subclass(TruncatedCanonicalNormalDistribution::class)
        subclass(NormalDistribution::class)
        subclass(CanonicalNormalDistribution::class)
    }
}
