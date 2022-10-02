package hooks

import kotlinx.js.timers.clearTimeout
import kotlinx.js.timers.setTimeout
import react.useEffect
import react.useEffectOnce
import react.useState

fun useDebounce(ms: Int, vararg dependencies: dynamic, callback: () -> Unit) {
    useEffect(*dependencies) {
        val timeout = setTimeout(callback, ms)
        cleanup { clearTimeout(timeout) }
    }
}