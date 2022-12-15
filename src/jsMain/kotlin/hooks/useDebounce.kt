package hooks

import web.timers.clearTimeout
import web.timers.setTimeout
import react.useEffect

fun useDebounce(ms: Int, vararg dependencies: dynamic, callback: () -> Unit) {
    useEffect(*dependencies) {
        val timeout = setTimeout(callback, ms)
        cleanup { clearTimeout(timeout) }
    }
}