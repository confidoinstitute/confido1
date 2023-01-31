package hooks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import react.useMemo
import react.useState
import utils.runCoroutine
import kotlin.coroutines.EmptyCoroutineContext

typealias Coroutine = suspend () -> Unit

data class CoroutineLock(val running: Boolean, private val call: (Coroutine) -> Unit) {
    operator fun invoke(coro: Coroutine): Unit = call(coro)
}

inline fun useCoroutineLock(): CoroutineLock {
    var running by useState(false)

    val call = useMemo {{ block: Coroutine ->
        if (!running)
        runCoroutine {
            running = true
            try {
                block()
            } catch (e: Throwable) {
                console.error(e)
            } finally {
                running = false
            }
        }
        Unit
    }}

    return CoroutineLock(running, call)
}
