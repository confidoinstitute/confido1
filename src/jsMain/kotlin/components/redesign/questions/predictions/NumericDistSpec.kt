package components.redesign.questions.predictions

import react.useMemo
import tools.confido.distributions.ContinuousProbabilityDistribution
import tools.confido.distributions.TruncatedNormalDistribution
import tools.confido.distributions.TruncatedSplitNormalDistribution
import tools.confido.spaces.NumericSpace
import tools.confido.utils.*
import kotlin.math.abs

sealed interface NumericDistSpec {
    val space: NumericSpace
    val dist: ContinuousProbabilityDistribution?
    val complete: Boolean
    fun identify(): String

    fun useDist() = useMemo(identify()) { dist }
}
sealed class NormalishDistSpec: NumericDistSpec {
    open val center: Double? = null
    abstract fun setCiBoundary(newCiBoundary: Double, side: Int? = null): NormalishDistSpec

    abstract fun setCenter(newCenter: Double): NormalishDistSpec
    abstract val ci: List2<Double>?
    abstract val asymmetric: Boolean
    abstract fun setAsymmetric(newAsymmetric: Boolean): NormalishDistSpec
    open fun coerceToRealistic() = this

    open val minCiRadius get() = space.size / 10000.0
    val coerceRanges get() =
        center?.let { center->
            List2(
                space.min..maxOf(center - minCiRadius, space.min),
                minOf(center + minCiRadius, space.max)..space.max
            )
        } ?:  List2(space.range, space.range)

    companion object {

        val ciConfidence = 0.8
        fun fromDist(dist: ContinuousProbabilityDistribution) =
            when (dist) {
                is TruncatedSplitNormalDistribution -> NumericDistSpecAsym(dist)
                is TruncatedNormalDistribution -> NumericDistSpecSym(dist)
                else -> NumericDistSpecAsym(dist.space, dist.median, List2(dist.icdf((1 - ciConfidence) / 2),
                        dist.icdf(1 - (1 - ciConfidence) / 2)))
            }
    }
}

data class NumericDistSpecSym(override val space: NumericSpace, override val center: Double? = null, val ciWidth: Double? = null): NormalishDistSpec() {
    override val asymmetric = false
    constructor(dist: TruncatedNormalDistribution): this(dist.space, dist.pseudoMean, dist.confidenceInterval(
        ciConfidence).size)
    override val dist: TruncatedNormalDistribution? by lazy {
        multiletNotNull(center, ciWidth) { center, ciWidth ->
            val pseudoStdev = binarySearch(0.0..4 * ciWidth, ciWidth, 30) {
                TruncatedNormalDistribution(space, center, it).confidenceInterval(ciConfidence).size
            }.mid
            TruncatedNormalDistribution(space, center, pseudoStdev)
        }
    }
    override val complete = center != null && ciWidth != null
    override fun setCenter(newCenter: Double) = copy (center = newCenter.coerceIn(space.range))
    override fun setCiBoundary(newCiBoundary: Double, side: Int?): NumericDistSpecSym {
        if (center == null) return this
        val effectiveNewBoundary = newCiBoundary.coerceIn(
            if (side==null) space.range else coerceRanges[side]
        )
        val desiredCiRadius = abs(center - effectiveNewBoundary)

        val newCiWidth = (if (center - desiredCiRadius < space.min)
            newCiBoundary - space.min
        else if (center + desiredCiRadius > space.max)
            space.max - newCiBoundary
        else
            2 * desiredCiRadius).coerceIn(minCiWidth..maxCiWidth)

        return NumericDistSpecSym(space, center, newCiWidth)
    }
    val minCiWidth = space.size / 1000.0
    val maxCiWidth get() = (0.798 * space.size)
    override val ci get() = if (center != null && ciWidth != null) {
        val ciRadius = ciWidth / 2.0
        if (center + ciRadius > space.max) List2(space.max - ciWidth,space.max)
        else if (center - ciRadius < space.min) List2(space.min,space.min + ciWidth)
        else List2(center - ciRadius,center + ciRadius)
    } else null

    override fun identify() = "S:$center:$ciWidth"

    fun toAsymmetric() =
        dist ?.let { dist->
            NumericDistSpecAsym(
                TruncatedSplitNormalDistribution(
                    dist.space,
                    dist.pseudoMean,
                    dist.pseudoStdev,
                    dist.pseudoStdev
                )
            )
        } ?: NumericDistSpecAsym(space, center, null)

    override fun setAsymmetric(newAsymmetric: Boolean) = if (newAsymmetric) toAsymmetric() else this
}


data class NumericDistSpecAsym(override val space: NumericSpace, override val center: Double?,
                                val ciRadii: List2<Double>?): NormalishDistSpec() {
    override val asymmetric = true
    override val complete = center != null && ciRadii != null
    override fun identify() = "S:$center:${ciRadii?.e1}:${ciRadii?.e2}"
    constructor(dist: TruncatedSplitNormalDistribution) : this(dist.space, dist.center,
        List2(maxOf(dist.center - dist.icdf(ciPercentiles.e1), 0.0),
        maxOf(dist.icdf(ciPercentiles.e2) - dist.center, 0.0)))
    override val dist: TruncatedSplitNormalDistribution? by lazy {
        center ?: return@lazy null
        ciRadii ?: return@lazy null
        TruncatedSplitNormalDistribution.findByCI(space, center,
            ciPercentiles.e1, (center - ciRadii.e1).coerceIn(coerceRanges.e1),
            ciPercentiles.e2, (center + ciRadii.e2).coerceIn(coerceRanges.e2),
            )
    }

    override fun setCenter(newCenter: Double) = copy(center = newCenter.coerceIn(space.range))

    override fun setCiBoundary(newCiBoundary: Double, side: Int?): NumericDistSpecAsym {
        if (center == null) return this
        val effectiveSide = side ?: if (newCiBoundary <= center) 0 else 1
        if ((effectiveSide == 0) != (newCiBoundary <= center)) return this
        val newCiRadius = if (effectiveSide == 0) center - newCiBoundary else newCiBoundary - center
        val oldRadii = ciRadii ?: List2(newCiRadius, newCiRadius)
        val newRadii = oldRadii.replace(effectiveSide, newCiRadius)
        return copy(ciRadii = newRadii)
    }


    override fun coerceToRealistic(): NumericDistSpecAsym {
        center ?: return this
        val newCiRadii = (ci ?: return  this).zip(ciPercentiles) { wantedCiBoundary, percentile ->
            val achievedCiBoundary = (dist ?: return this).icdf(percentile)

            val newCiBoundary  = if (abs(achievedCiBoundary - wantedCiBoundary) / abs(space.size) > 1e-5) {
                achievedCiBoundary
            } else {
                wantedCiBoundary
            }

            abs(newCiBoundary - center)
        }
        return copy(ciRadii = newCiRadii)
    }

    override val ci get() = if (center != null && ciRadii != null) {
        List2((center - ciRadii.e1).coerceIn(coerceRanges.e1),
            (center + ciRadii.e2).coerceIn(coerceRanges.e2))
    } else null

    fun toSymmetric() = NumericDistSpecSym(space, center, ciRadii?.sum())
    override fun setAsymmetric(newAsymmetric: Boolean) = if (newAsymmetric) this else toSymmetric()

    companion object {
        val ciPercentiles = List2((1 - ciConfidence) / 2, 1 - (1 - ciConfidence) / 2)
    }
}

