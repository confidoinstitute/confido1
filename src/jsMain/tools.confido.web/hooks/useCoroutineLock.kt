package tools.confido.web.hooks

import tools.confido.web.ApiError
import tools.confido.web.components.redesign.basic.showError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import react.useMemo
import react.useRef
import react.useState
import tools.confido.web.utils.runCoroutine
import kotlin.coroutines.EmptyCoroutineContext

typealias Coroutine = suspend () -> Unit

data class CoroutineLock(val running: Boolean, private val call: (Coroutine) -> Unit) {
    operator fun invoke(coro: Coroutine): Unit = call(coro)
}

inline fun useCoroutineLock(): CoroutineLock {
    var running by useState(false)
    // The guard must read a ref, not the state variable: useMemo here has an empty
    // dependency array, so the lambda captures the first render's `running` forever
    // and a state-based check would always see false.
    val runningRef = useRef(false)

    val call = useMemo {{ block: Coroutine ->
        if (runningRef.current != true) {
            runningRef.current = true
            runCoroutine {
                running = true
                try {
                    block()
                } catch (e: ApiError) {
                    showError(e.message)
                } catch (e: Throwable) {
                    console.error(e)
                } finally {
                    runningRef.current = false
                    running = false
                }
            }
        }
        Unit
    }}

    return CoroutineLock(running, call)
}
