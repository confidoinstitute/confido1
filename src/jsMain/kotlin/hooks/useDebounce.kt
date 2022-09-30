package hooks

import kotlinx.js.timers.clearTimeout
import kotlinx.js.timers.setTimeout
import react.useEffect

fun useDebounce(ms: Int, vararg dependencies: dynamic, callOnUnmount: Boolean = false, callback: () -> Unit) {
    useEffect(*dependencies) {
        val timeout = setTimeout(callback, ms)
        cleanup { clearTimeout(timeout) }
    }
    useEffect {
        if (callOnUnmount) {
            cleanup {
                callback()
            }
        }
    }
}