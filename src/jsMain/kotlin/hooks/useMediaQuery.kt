package hooks

import browser.window
import react.useCallback
import react.useEffect
import react.useMemo
import react.useState
import web.events.Event

fun useMediaQuery(query: String): Boolean {
    val matchMedia = window.asDynamic().matchMedia
    if (!matchMedia == null) {
        return false
    }

    val mediaMatcher = useMemo { matchMedia(query) }

    var cur by useState(mediaMatcher.matches as Boolean)
    val onChange = useCallback { ev: Event ->
        cur = mediaMatcher.matches
    }

    useEffect {
        // Safari 13.1 does not have `addEventListener`, but does have `addListener`
        if (mediaMatcher.addEventListener != null) {
            mediaMatcher.addEventListener("change", onChange)
        } else {
            mediaMatcher.addListener(onChange)
        }
        cur = mediaMatcher.matches

        cleanup {
            if (mediaMatcher.removeEventListener != null) {
                mediaMatcher.removeEventListener("change", onChange)
            } else {
                mediaMatcher.removeListener(onChange)
            }
        }
    }
    return cur
}
fun <T> useBreakpoints(vararg breakpoints: Pair<T, Number>, default: T): T {
    val sorted = useMemo { breakpoints.sortedBy { it.second.toDouble() } }
    // cannot short-circuit here, must call all the useMediaQuery for hook consistency
    val queryRes = sorted.map { it.first to useMediaQuery("screen and (max-width: ${it.second}px)") }
    return queryRes.firstOrNull{ it.second }?.first ?: default
}
