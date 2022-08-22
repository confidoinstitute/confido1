package utils

inline fun jsObject(init: dynamic.() -> Unit): dynamic {
    val o = js("{}")
    init(o)
    return o
}

fun Double.format(digits: Int): String = asDynamic().toFixed(digits)
