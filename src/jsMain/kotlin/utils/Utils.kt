package utils

inline fun jsObject(init: dynamic.() -> Unit): dynamic {
    val o = js("{}")
    init(o)
    return o
}

fun Double.format(digits: Int): String = asDynamic().toFixed(digits)

fun linearSpace(first: Double, last: Double, step: Double): Sequence<Double> {
    var current = first
    return generateSequence {
        val snap = current
        current += step
        if (snap <= last) snap else null
    }
}