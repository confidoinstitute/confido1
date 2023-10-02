package hooks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import react.EffectBuilder
import react.useEffect
import react.useEffectOnce
import react.useState
import kotlin.coroutines.EmptyCoroutineContext

fun EffectBuilder.coroutineEffect(fn: suspend CoroutineScope.()->Unit) {
    val job = CoroutineScope(EmptyCoroutineContext).launch(block=fn)
    cleanup {
        job.cancel()
    }
}
fun useCoroutineOnce(fn: suspend CoroutineScope.()->Unit) = useEffectOnce { coroutineEffect(fn) }
fun useCoroutine(vararg dependencies: Any?, fn: suspend CoroutineScope.()->Unit) =
    if (dependencies.isEmpty()) useCoroutineOnce(fn) // probably does not make sense to use version without dependencies
    else useEffect(*dependencies) { coroutineEffect(fn) }
fun <T> useSuspendResult(vararg dependencies: Any?, resetOnChange: Boolean = true, f: suspend CoroutineScope.() -> T): T? {
    var res by useState<T?>(null)
    if (dependencies.isEmpty())
        useCoroutineOnce { res = f() }
    else
        useCoroutine(*dependencies) {
            if (resetOnChange) res = null
            res = f()
        }
    return res
}