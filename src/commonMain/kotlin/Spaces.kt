package tools.confido.spaces

import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.*
import kotlinx.serialization.modules.*
import tools.confido.utils.*
import kotlin.jvm.JvmName
import kotlin.math.ceil
import kotlin.math.log10


@Serializable
sealed class Space {
    abstract val bins: Int
    abstract fun checkValue(value: Any): Boolean
    abstract fun value2bin(value: Any): Int?
    abstract fun formatValue(value: Any, showUnit: Boolean = true, condensed: Boolean = false): String
    abstract fun identify(): String
}


sealed class TypedSpace<T : Any> : Space() {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("_checkValue")
    abstract fun checkValue(value : T): Boolean
    final override fun checkValue(value: Any): Boolean {
        try {
            @Suppress("UNCHECKED_CAST")
            return checkValue(value as T)
        } catch (e: ClassCastException) {
            return false
        }
    }


    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("_formatValue")
    abstract fun formatValue(value : T, showUnit: Boolean = true, condensed: Boolean = false): String
    final override fun formatValue(value: Any, showUnit: Boolean, condensed: Boolean): String {
        if (!checkValue(value)) return "(invalid)"
        try {
            @Suppress("UNCHECKED_CAST")
            return formatValue(value as T, showUnit, condensed)
        } catch (e: ClassCastException) {
            return "(invalid)"
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("_value2bin")
    abstract fun value2bin(value : T): Int?
    final override fun value2bin(value: Any): Int? {
        if (!checkValue(value)) return null
        @Suppress("UNCHECKED_CAST")
        return value2bin(value as T)
    }
}

// TODO: Allow customizing choices and change this into a dataclass
// Allow either a few standard choice-sets (e.g. YES_NO, TRUE_FALSE, AGREE_DISAGREE...)
// that will be autotranslated... or custom string pair
@Serializable
@SerialName("binary")
object BinarySpace : TypedSpace<Boolean>() {
    override val bins: Int = 2
    override fun checkValue(value: Boolean) = true

    override fun formatValue(value: Boolean, showUnit: Boolean, condensed: Boolean): String =
        if (value) "Yes" else "No" // TODO translations, custom strings

    override fun value2bin(value: Boolean): Int? =
        if (value) 1 else 0

    override fun identify() = "binary"
}

class Binner(val space : NumericSpace, val bins: Int) {
    val binSize =  (space.max - space.min) / bins

    val binRanges
        get() = GeneratedList<OpenEndRange<Double>>(bins) {
            val start = space.min + binSize * it
            (start ..< start+binSize)
        }
    val binBorders
        get() = GeneratedList<Double>(bins+1) {space.min + it*binSize}
    val binMidpoints
        get() = GeneratedList<Double>(bins) { space.min + it*binSize + binSize / 2 }

    fun value2bin(value: Double): Int? {
        if (value < space.min || value > space.max) return null
        return ((value - space.min) / binSize).toInt()
    }
}

@Serializable
@SerialName("numeric")
data class NumericSpace(
    val min: Double = Double.NEGATIVE_INFINITY,
    val max: Double = Double.POSITIVE_INFINITY,
    override val bins: Int = DEFAULT_BINS,
    val representsDays: Boolean = false,
    val unit: String = "",
    var decimals: Int = 1
) : TypedSpace<Double>() {

    @Transient
    val isInfinite = min.isInfinite() || max.isInfinite()

    @Transient
    val binner = Binner(this, bins)

    val size get() = max - min

    val range get() = min..max

    val reasonableDecimals get() = ceil(maxOf(-log10(size / 1000), 0.0)).toInt()

    fun subspace(min: Double, max: Double): NumericSpace {
        return this.copy(min = maxOf(this.min,min), max = minOf(this.max,max))
    }

    infix fun issubspace(other: NumericSpace): Boolean {
        return this.min >= other.min && this.max <= other.max && this.representsDays == other.representsDays
    }

    override fun checkValue(value: Double) = value in min..max

    override fun formatValue(value: Double, showUnit: Boolean, condensed: Boolean): String {
        if (representsDays) {
            return LocalDate.utcFromUnix(value.toLong()).toString()
        }
        var r = if (condensed)
                condensedNum(value)
            else
                value.toFixed(decimals)
        if (unit != "" && showUnit) r += " $unit"
        return r
    }

    fun formatDifference(value: Double, showUnit: Boolean = true): String {
        if (representsDays) {
            return "${(value / 86400).toFixed(1)} days"
        }
        var r = value.toFixed(decimals)
        if (unit != "" && showUnit) r += " $unit"
        return r
    }

    override fun value2bin(value: Double) = value2bin(value, this.binner)
    fun value2bin(value: Double, binner: Binner) = binner.value2bin(value)
    fun value2bin(value: Double, bins: Int) = Binner(this, bins).value2bin(value)

    override fun identify() = "num:$min:$max:$representsDays"

    companion object {
        const val DEFAULT_BINS = 1000
        fun fromDates(minDate: LocalDate, maxDate: LocalDate): NumericSpace {
            val min = minDate.toEpochDays() * 86400.0
            val max = maxDate.toEpochDays() * 86400.0
            val diff = maxDate.toEpochDays() - minDate.toEpochDays() + 1
            val bins = diff.coerceAtMost(1000)
            return NumericSpace( min, max, bins=bins, representsDays = true)
        }
    }
}

@Serializable
sealed class Value {
    abstract val space: Space
    abstract val value: Any
    open fun format() = space.formatValue(value)

    open fun identify() = "${space.identify()}:$value"
}

sealed class TypedValue<V: Any, S: TypedSpace<V>> : Value() {
    override abstract val space: S
    override abstract val value: V
    // this calls the typed version (TypedSpace<T>.formatValue) directly
    override fun format() = space.formatValue(value)

    /**
     * This should be called from all subclass constructors. Unfortunately we
     * cannot do this in our constructor because ̈́'space' and 'value' are
     * initialized after it is called.
     */
    protected fun check() {
        require(space.checkValue(value))
    }
}

@Serializable
@SerialName("numeric")
data class NumericValue(
    override val space: NumericSpace,
    override val value: Double
) : TypedValue<Double, NumericSpace>() {
    init { check() }
}

@Serializable
@SerialName("binary")
data class BinaryValue(
    override val value: Boolean
) : TypedValue<Boolean, BinarySpace>() {
    @Transient
    override val space = BinarySpace
    init { check() }
}

