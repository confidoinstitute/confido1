package hooks

import web.timers.clearTimeout
import web.timers.setTimeout
import react.useEffect
import react.useEffectWithCleanup

/**
 * When dependencies change, wait until they are stable long enough and then run the callback.
 */
fun useDebounce(ms: Int, vararg dependencies: dynamic, callback: () -> Unit) {
    useEffectWithCleanup(*dependencies) {
        val timeout = setTimeout(callback, ms)
        onCleanup { clearTimeout(timeout) }
    }
}