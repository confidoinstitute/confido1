package tools.confido.utils

import kotlin.math.roundToLong

actual fun Double.toFixed(decimals: Int): String {
    var x = toDouble()
    repeat(decimals) { x *= 10.0 }
    var s = x.roundToLong().toString()
    if (s.length < decimals + 1)
        s = "0".repeat(decimals+1 - s.length) + s
    val decStart = s.length - decimals
    val decPart = s.substring(decStart)
    val wholePart = s.substring(0 until decStart)
    return "${wholePart}.${decPart}"
}
