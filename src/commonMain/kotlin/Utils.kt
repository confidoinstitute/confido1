@file:Suppress("DANGEROUS_CHARACTERS")

package tools.confido.utils

import kotlinx.datetime.*
import kotlin.js.JsName
import kotlin.jvm.JvmName
import kotlin.math.roundToInt

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

fun String.mapFirst(f: (Char) -> String): String = if (isEmpty()) "" else f(this[0]) + this.substring(1)
fun String.capFirst() = mapFirst { it.uppercase() }
fun String.uncapFirst() = mapFirst { it.lowercase() }

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

expect fun Double.toFixed(decimals: Int): String

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

interface HasUrlPrefix {
    val urlPrefix: String
}

const val TOKEN_LEN = 32

fun pluralize(word: String, count: Int): String {
    return when (count) {
        1 -> word
        else -> "${word}s"
    }
}

/// convert a number to string without extra trailing zero decimals
fun toNiceStr(n: Number, maxDecimals: Int) =
    when (n) {
        //is Int -> n.toString() // XXX seems broken
        else-> n.toDouble().toFixed(maxDecimals).trimEnd('0').trimEnd('.', ',')
    }
fun condensedNum(n: Number) =  n.toDouble().let { d->
    if (d >= 1_000_000_000.0) {
        toNiceStr(d / 1_000_000_000.0, 1) + "B"
    } else if (d >= 1_000_000.0) {
        toNiceStr(d / 1_000_000.0, 1) + "M"
    } else if (d >= 1000.0) {
        toNiceStr(d / 1000.0, 1) + "K"
    } else {
        toNiceStr(n, 1)
    }
}
val ClosedRange<Double>.size get() = endInclusive - start
val ClosedRange<Double>.mid get() = (start + endInclusive)/2.0
