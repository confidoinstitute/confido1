package tools.confido.utils

actual fun Double.toFixed(decimals: Int): String = this.asDynamic().toFixed(decimals) as String
