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

    override val mean: Double = value
    override val stdev: Double = 0.0
    override val maxDensity: Double = Double.POSITIVE_INFINITY
    override val meanIsDiscretized: Boolean = false
    override val stdevIsDiscretized: Boolean = false
    override val preferredCICenter: Double = value

    override fun identify() = "point:$value"
}

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
    }

    override fun registerContinuousProbabilityDistributions(builder: PolymorphicModuleBuilder<ContinuousProbabilityDistribution>) {
        builder.subclass(PointEstimateContinuousDistribution::class)
    }
}
