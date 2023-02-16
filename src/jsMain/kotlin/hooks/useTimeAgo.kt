package hooks

import react.useEffect
import react.useState
import tools.confido.utils.pluralize
import tools.confido.utils.unixNow
import web.timers.clearInterval
import web.timers.setInterval

private fun agoPrefix(timestamp: Int): String {
    return when (val diff = unixNow() - timestamp) {
        in 0..120 -> "$diff ${pluralize("second", diff)}"
        in 120..7200 -> {
            val minutes = diff / 60
            "$minutes ${pluralize("minute", minutes)}"
        }
        in 7200..172800 -> {
            val hours = diff / 3600
            "$hours ${pluralize("hour", hours)}"
        }
        else -> {
            val days = diff / 86400
            "$days ${pluralize("day", days)}"
        }
    }
}

/**
 * Returns a string indicating how old a timestamp is.
 *
 * The result is null if and only if [timestamp] is null.
 */
fun useTimeAgo(timestamp: Int?): String? {
    fun format(prefix: String) = "$prefix ago"

    var text by useState<String?>(null)

    useEffect(timestamp) {
        fun setText() {
            if (timestamp != null) {
                text = format(agoPrefix(timestamp))
            } else {
                text = null
            }
        }
        setText()
        val interval = setInterval(::setText, 5000)

        cleanup {
            clearInterval(interval)
        }
    }

    return text
}
