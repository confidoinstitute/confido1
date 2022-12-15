package tools.confido.utils

import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.js.JsName
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName
import kotlin.math.roundToInt
import kotlin.math.roundToLong

operator fun Number.compareTo(b : Number): Int {
    if ((this is Int || this is Short || this is Long || this is Byte || this is Float || this is Double)
        && (b is Int || b    is Short || b    is Long || b    is Byte || b    is Float || b    is Double))
            return this.compareTo(b)
    else throw NotImplementedError()
}

class GeneratedList<T>(override val size: Int, val gen: (Int) -> T) : List<T> {
    class GeneratedListIterator<T>(val lst: GeneratedList<T>, var pos: Int = 0): ListIterator<T> {
        override fun hasNext() = pos < lst.size
        override fun next(): T = if (hasNext()) lst[pos++] else throw NoSuchElementException()
        override fun hasPrevious() = pos > 0
        override fun previous() = if (hasPrevious()) lst[--pos] else throw NoSuchElementException()
        override fun nextIndex() = pos + 1
        override fun previousIndex() = pos - 1
    }
    override fun isEmpty() = size==0
    override fun containsAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(index: Int) = gen(index)
    override fun indexOf(element: T) = asSequence().indexOf(element)

    override fun contains(element: T) = asSequence().contains(element)

    override fun listIterator(index: Int) = GeneratedListIterator<T>(this, index)
    override fun subList(fromIndex: Int, toIndex: Int): List<T> {
        if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) throw IndexOutOfBoundsException()
        return GeneratedList<T>(toIndex-fromIndex, { gen(fromIndex+it)})
    }

    override fun listIterator() = GeneratedListIterator<T>(this)
    override fun iterator() = listIterator()
    override fun lastIndexOf(element: T) = (0 until size).reversed().first { gen(it) == element }

}

val alnum = ('a'..'z').toList() + ('0'..'9').toList()
fun randomString(length: Int) =
    (1..length).map {
        alnum.random()
    }.joinToString("")

fun generateId() = randomString(16)

fun formatPercent(value: Number, space: Boolean=true): String = "${(value.toDouble()*100).roundToInt()}${if (space) " " else ""}%"

fun Double.clamp(range: ClosedRange<Double>): Double {
    if (this < range.start) return range.start
    if (this > range.endInclusive) return range.endInclusive
    return this
}

fun Double.clamp01() = clamp(0.0..1.0)

fun Number.toFixed(decimals: Int): String {
    var x = toDouble()
    repeat(decimals) { x *= 10 }
    var s = x.roundToInt().toString()
    if (s.length < decimals + 1)
        s = "0".repeat(decimals+1 - s.length) + s
    val decStart = s.length - decimals
    val decPart = s.substring(decStart)
    val wholePart = s.substring(0 until decStart)
    return "${wholePart}.${decPart}"
}
fun unixNow(): Int = (Clock.System.now().toEpochMilliseconds()/1000).toInt()

fun LocalDate.Companion.fromUnix(ts: Number) = Instant.fromEpochSeconds(ts.toLong()).toLocalDateTime(TimeZone.currentSystemDefault()).date
fun LocalDate.Companion.utcFromUnix(ts: Number) = Instant.fromEpochSeconds(ts.toLong()).toLocalDateTime(TimeZone.UTC).date

fun zeros(size: Int) = List(size) { _ -> 0.0 }

// VECTOR OPERATIONS - inspired by Raku
@JsName("Zplus")
@JvmName("Zplus")
infix fun List<Double>.`Z+`(other: List<Double>) = zip(other) { x,y -> x+y }
@JsName("Zminus")
@JvmName("Zminus")
infix fun List<Double>.`Z-`(other: List<Double>) = zip(other) { x,y -> x-y }
@JsName("Ztimes1")
@JvmName("Ztimes1")
infix fun Number.`Z*`(other: List<Double>) = other.map { this.toDouble() * it }
@JsName("Ztimes2")
@JvmName("Ztimes2")
infix fun List<Double>.`Z*`(other: Number) = this.map { other.toDouble() * it }
// Aparently, `Z/` is not a valid identifier.
infix fun Number.Zdiv(other: List<Double>) = other.map { this.toDouble() / it }
infix fun List<Double>.Zdiv(other: Number) = this.map { it / other.toDouble() }

// normalize list so that sum is 1
fun List<Double>.normalize(): List<Double> {
    val s = this.sum()
    if (s == 0.0) return this
    return this Zdiv s
}

fun <K,V,R> Map<K,V>.mapValuesNotNull(f: (Map.Entry<K,V>) -> R?) = mapNotNull {
    val mapped = f(it)
    if (mapped == null) null
    else it.key to mapped
}.toMap()
