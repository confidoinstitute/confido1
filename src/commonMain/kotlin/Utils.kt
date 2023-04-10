@file:Suppress("DANGEROUS_CHARACTERS")

package tools.confido.utils

import kotlinx.datetime.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.JsName
import kotlin.jvm.JvmName
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

}class List2Serializer<T>(private val elementSerializer: KSerializer<T>) : KSerializer<List2<T>> {
    override val descriptor: SerialDescriptor = ListSerializer(elementSerializer).descriptor
    override fun serialize(encoder: Encoder, value: List2<T>) {
        encoder.encodeSerializableValue(ListSerializer(elementSerializer), value)
    }
    override fun deserialize(decoder: Decoder): List2<T> {
        val lst = decoder.decodeSerializableValue(ListSerializer(elementSerializer))
        return List2(lst)
    }
}

// A fixed-size two element list. Unlinke a Pair, both elements are of the same type,
// and it can be iterated over and use functions like map and zip. Used to pass two
// objects of the same type, for example two probability distributions to compare.
@Serializable(with=List2Serializer::class)
open class List2<out T>(val lst: List<T>) : List<T> by lst {
    init { require(lst.size == 2) }
    constructor(e1: T, e2: T) : this(listOf(e1,e2))

    inline fun <U> map(f: (T) -> U): List2<U> = List2(lst.map(f))
    inline fun <U> zip(other: List2<U>): List2<Pair<T,U>> = List2(lst.zip(other))
    inline fun <K: Comparable<K>> sortedBy(crossinline f: (T) -> K): List2<T> = List2(lst.sortedBy(f))
    fun reversed() = List2(lst.reversed())
    override val size get() = 2

    // sometimes it is useful to index with booleans
    operator fun get(idx: Boolean) = get(if(idx) 1 else 0)

    val e1 get() = lst[0]
    val e2 get() = lst[1]

    fun component1() = lst[0]
    fun component2() = lst[1]
    // override extension functions List<T>.componentN() to get compile-time warning
    fun component3(): Nothing = throw IndexOutOfBoundsException()
    fun component4(): Nothing = throw IndexOutOfBoundsException()
    fun component5(): Nothing = throw IndexOutOfBoundsException()
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
@JsName("Zplus2")
@JvmName("Zplus2")
infix fun List2<Double>.`Z+`(other: List2<Double>) = List2(zip(other) { x,y -> x+y })
@JsName("Zminus")
@JvmName("Zminus")
infix fun List<Double>.`Z-`(other: List<Double>) = zip(other) { x,y -> x-y }
@JsName("Zminus2")
@JvmName("Zminus2")
infix fun List2<Double>.`Z-`(other: List2<Double>) = List2(zip(other) { x,y -> x-y })
@JsName("Ztimes1")
@JvmName("Ztimes1")
infix fun Number.`Z*`(other: List<Double>) = other.map { this.toDouble() * it }
@JsName("Ztimes2")
@JvmName("Ztimes2")
infix fun List<Double>.`Z*`(other: Number) = this.map { other.toDouble() * it }

@JsName("Ztimes3")
@JvmName("Ztimes3")
infix fun Number.`Z*`(other: List2<Double>) = List2(other.map { this.toDouble() * it })
@JsName("Ztimes4")
@JvmName("Ztimes4")
infix fun List2<Double>.`Z*`(other: Number) = List2(this.map { other.toDouble() * it })
// Aparently, `Z/` is not a valid identifier.
infix fun Number.Zdiv(other: List<Double>) = other.map { this.toDouble() / it }
infix fun List<Double>.Zdiv(other: Number) = this.map { it / other.toDouble() }
infix fun Number.Zdiv(other: List2<Double>) = List2(other.map { this.toDouble() / it })
infix fun List2<Double>.Zdiv(other: Number) = List2(this.map { it / other.toDouble() })

        // normalize list so that sum is 1
fun List<Double>.normalize(): List<Double> {
    val s = this.sum()
    if (s == 0.0) return this
    return this Zdiv s
}
fun List2<Double>.normalize(): List2<Double> {
    val s = this.sum()
    if (s == 0.0) return this
    return this Zdiv s
}

fun List2<Double>.length() = sqrt(e1*e1 + e2*e2)

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

inline fun <T1, R> multilet(x: T1, body: (T1)->R) =  body(x)
inline fun <T1, T2, R> multilet(a1: T1, a2: T2, body: (T1, T2)->R) =  body(a1, a2)
inline fun <T1, T2, T3, R> multilet(a1: T1, a2: T2, a3: T3, body: (T1, T2, T3)->R)
        =  body(a1, a2, a3)
inline fun <T1, T2, T3, T4, R> multilet(a1: T1, a2: T2, a3: T3, a4: T4, body: (T1, T2, T3, T4)->R)
        =  body(a1, a2, a3, a4)
inline fun <T1: Any, R:Any> multiletNotNull(x: T1?, body: (T1)->R?) = if (x != null) body(x) else null
inline fun <T1: Any, T2: Any, R:Any> multiletNotNull(a1: T1?, a2: T2?, body: (T1, T2)->R?) = if (a1 != null && a2 != null) body(a1, a2) else null
inline fun <T1: Any, T2: Any, T3: Any, R:Any> multiletNotNull(a1: T1?, a2: T2?, a3: T3?, body: (T1, T2, T3)->R?)
    = if (a1 != null && a2 != null && a3 != null) body(a1, a2, a3) else null
inline fun <T1: Any, T2: Any, T3: Any, T4: Any, R:Any> multiletNotNull(a1: T1?, a2: T2?, a3: T3?, a4: T4?, body: (T1, T2, T3, T4)->R?)
        = if (a1 != null && a2 != null && a3 != null && a4 != null) body(a1, a2, a3, a4) else null
