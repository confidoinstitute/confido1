package extensions

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import tools.confido.distributions.*
import tools.confido.extensions.*
import tools.confido.spaces.*
import kotlin.math.*

val PointEstimateKey = ExtensionDataKeyWithDefault<Boolean>("point_estimate", false)

@Serializable
data class WeightedPointEstimate(
    val value: Double,
    val weight: Double
)

@Serializable
@SerialName("multiPointEstimate")
data class MultiPointEstimateContinuousDistribution(
    override val space: NumericSpace,
    val estimates: List<WeightedPointEstimate>
) : ContinuousProbabilityDistribution {
    init {
        require(estimates.isNotEmpty()) { "Must have at least one point estimate" }
        require(estimates.all { it.value in space.range }) { "All point estimates must be within space range" }
        require(estimates.all { it.weight > 0 }) { "All weights must be positive" }
    }

    private val normalizedEstimates by lazy {
        val totalWeight = estimates.sumOf { it.weight }
        estimates.map { WeightedPointEstimate(it.value, it.weight / totalWeight) }
    }

    override fun pdf(x: Double): Double {
        // Return infinite density at each point, weighted by the normalized weight
        return normalizedEstimates.sumOf { est ->
            if (abs(x - est.value) < 1e-10) est.weight * Double.POSITIVE_INFINITY else 0.0
        }
    }

    override fun cdf(x: Double): Double {
        // Sum weights of all points less than or equal to x
        return normalizedEstimates.filter { it.value <= x }.sumOf { it.weight }
    }

    override fun icdf(p: Double): Double {
        return when {
            p <= 0.0 -> space.min
            p >= 1.0 -> space.max
            else -> {
                var cumWeight = 0.0
                for (est in normalizedEstimates) {
                    cumWeight += est.weight
                    if (cumWeight >= p) return est.value
                }
                normalizedEstimates.last().value
            }
        }
    }

    override val mean: Double by lazy {
        normalizedEstimates.sumOf { it.value * it.weight }
    }

    override val stdev: Double by lazy {
        val variance = normalizedEstimates.sumOf { est ->
            est.weight * (est.value - mean).pow(2)
        }
        sqrt(variance)
    }

    override val maxDensity: Double = Double.POSITIVE_INFINITY
    override val meanIsDiscretized: Boolean = false
    override val stdevIsDiscretized: Boolean = false
    override val preferredCICenter: Double = mean

    override fun identify() = "multi_point:" + normalizedEstimates.joinToString(":") { "${it.value},${it.weight}" }
}


@Serializable
@SerialName("pointEstimate")
data class PointEstimateContinuousDistribution(
    override val space: NumericSpace,
    val value: Double
) : ContinuousProbabilityDistribution {
    init {
        require(value in space.range) { "Point estimate value must be within space range" }
    }

    override fun pdf(x: Double): Double = if (abs(x - value) < 1e-10) Double.POSITIVE_INFINITY else 0.0
    override fun cdf(x: Double): Double = if (x < value) 0.0 else 1.0
    override fun icdf(p: Double): Double = when {
        p <= 0.0 -> space.min
        p >= 1.0 -> space.max
        else -> value
    }

    override fun confidenceInterval(p: Double, preferredCenter: Double): ClosedFloatingPointRange<Double> = value..value

    override val mean: Double = value
    override val stdev: Double = 0.0
    override val maxDensity: Double = Double.POSITIVE_INFINITY
    override val meanIsDiscretized: Boolean = false
    override val stdevIsDiscretized: Boolean = false
    override val preferredCICenter: Double = value

    override fun identify() = "point:$value"
}



@Serializable
data class PointEstimateWithUser(
    val nickname: String,
    val value: Double,
    val isSpecial: Boolean = false
)

open class PointEstimateExtension : Extension {
    override val extensionId = "point_estimate"

    override fun registerEdtKeys(edt: ExtensionDataType) {
        when (edt.name) {
            "QuestionEDT" -> {
                edt.add(PointEstimateKey)
            }
        }
    }

    override fun registerProbabilityDistributions(builder: PolymorphicModuleBuilder<ProbabilityDistribution>) {
        builder.subclass(PointEstimateContinuousDistribution::class)
        builder.subclass(MultiPointEstimateContinuousDistribution::class)
    }

    override fun registerContinuousProbabilityDistributions(builder: PolymorphicModuleBuilder<ContinuousProbabilityDistribution>) {
        builder.subclass(PointEstimateContinuousDistribution::class)
        builder.subclass(MultiPointEstimateContinuousDistribution::class)
    }
}
