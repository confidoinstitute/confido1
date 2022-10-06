package tools.confido.utils

import kotlinx.datetime.*
import kotlin.math.roundToInt
import kotlin.math.roundToLong

operator fun Number.compareTo(b : Number): Int {
    if ((this is Int || this is Short || this is Long || this is Byte || this is Float || this is Double)
        && (b is Int || b    is Short || b    is Long || b    is Byte || b    is Float || b    is Double))
            return this.compareTo(b)
    else throw NotImplementedError()
}


val alnum = ('a'..'z').toList() + ('0'..'9').toList()
fun randomString(length: Int) =
    (1..length).map {
        alnum.random()
    }.joinToString("")

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
