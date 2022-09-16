package utils

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max

inline fun jsObject(init: dynamic.() -> Unit): dynamic {
    val o = js("{}")
    init(o)
    return o
}

fun Double.format(digits: Int): String = asDynamic().toFixed(digits)

fun linearSpace(first: Double, last: Double, step: Double) = sequence {
    var current = first
    while (current <= last) {
        yield(current)
        current += step
    }
}

fun roundNumbers() = sequence {
    var base = 1
    while (true) {
        yield(base)
        yield(2*base)
        yield(5*base)
        base *= 10
    }
}

fun markSpacing(width: Double, start: Double, end: Double): List<Double> {
    val range = end - start
    val unitWidth = width / range

    if (width == 0.0) return emptyList()

    fun strLength(x: Double) = x.toString().length

    val markBase = roundNumbers().takeWhile{it <= range}.find {step ->
        val lastMark = floor(end / step) * step
        (width / (range / step) >= strLength(lastMark) * 20)
    } ?: return emptyList()

    var firstMark = ceil(start / markBase) * markBase
    if (unitWidth * (firstMark - start) < 20 * strLength(firstMark)) firstMark += markBase

    var lastMark = floor(end / markBase) * markBase
    if (unitWidth * (end - lastMark) < 20 * strLength(lastMark)) lastMark -= markBase

    return sequence {
        yield(start)
        var mark = firstMark
        while (mark <= lastMark) {
            yield(mark)
            mark += markBase
        }
        yield(end)

    }.toList()
}