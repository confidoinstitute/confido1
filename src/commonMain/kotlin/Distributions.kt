package tools.confido.distributions
import kotlin.math.*

interface ProbabilityDistribution {
    fun pdf(x: Double): Double
    fun cdf(x: Double): Double
    fun icdf(p: Double): Double

    fun probabilityBetween(start: Double, end: Double) = cdf(end) - cdf(start)
    fun densityBetween(start: Double, end: Double) = probabilityBetween(start, end) / (end - start)
    fun confidenceInterval(p: Double): Pair<Double, Double>
}

class CanonicalNormalDistribution : ProbabilityDistribution {
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

        if (p < pL) return tail(sqrt(-ln(p) * 2))
        if (p > pH) return -tail(sqrt(-ln(1 - p) * 2))

        val q = p - 0.5
        val r = q*q
        return (((((a0*r + a1)*r + a2)*r + a3)*r + a4)*r + a5) * q /
               (((((b0*r + b1)*r + b2)*r + b3)*r + b4)*r + 1)

    }

    override fun confidenceInterval(p: Double) = Pair(icdf(1-p)/2, icdf(1+p)/2)
}

class NormalDistribution(val mean: Double, val stdDev: Double) : ProbabilityDistribution {
    val dist = CanonicalNormalDistribution()

    fun xform(x: Double) = (x - mean) / stdDev
    fun xformInv(x: Double) = x * stdDev + mean

    override fun pdf(x: Double) = dist.pdf(xform(x))
    override fun cdf(x: Double) = dist.cdf(xform(x))
    override fun icdf(p: Double) = dist.icdf(xformInv(p))
    override fun confidenceInterval(p: Double) = Pair(icdf(1-p)/2, icdf(1+p)/2)
}

class TruncatedNormalDistribution(val mean: Double, val stdDev: Double, val min: Double, val max: Double) : ProbabilityDistribution {
    val dist = NormalDistribution(mean, stdDev)
    val pIn = dist.probabilityBetween(min, max)
    val pLT = dist.cdf(min)

    override fun pdf(x: Double): Double {
        if (x < min) return 0.0
        if (x > max) return 0.0
        return dist.pdf(x) / pIn
    }

    override fun cdf(x: Double): Double {
        if (x <= min) return 0.0
        if (x >= max) return 1.0
        return dist.probabilityBetween(min, x) / pIn
    }

    override fun icdf(p: Double): Double = dist.icdf(p * pIn + pLT)

    override fun confidenceInterval(p: Double): Pair<Double, Double> {
        val pGlobal = p * pIn
        val (ciLower, ciUpper) = dist.confidenceInterval(pGlobal)
        if (ciLower < min) return Pair(min, icdf(p))
        if (ciUpper > max) return Pair(icdf(1-p), max)
        return Pair(ciLower, ciUpper)
    }
}