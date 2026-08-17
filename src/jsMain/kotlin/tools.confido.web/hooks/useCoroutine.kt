package tools.confido.web.hooks

import tools.confido.web.ApiError
import tools.confido.web.components.redesign.basic.showError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import react.CleanupBuilder
import react.useEffectOnceWithCleanup
import react.useEffectWithCleanup
import react.useState
import kotlin.coroutines.EmptyCoroutineContext

/** Effect-scoped coroutine (cancelled on cleanup). Like runCoroutine, an escaping
 *  [ApiError] becomes an error toast — effects that fire actions get the default
 *  failure mode; data *loads* belong in [useSuspendResult], which renders null instead. */
fun CleanupBuilder.coroutineEffect(fn: suspend CoroutineScope.()->Unit) {
    val job = CoroutineScope(EmptyCoroutineContext).launch {
        try { fn() } catch (e: ApiError) { showError(e.message) }
    }
    onCleanup {
        job.cancel()
    }
}
fun useCoroutineOnce(fn: suspend CoroutineScope.()->Unit) = useEffectOnceWithCleanup { coroutineEffect(fn) }
fun useCoroutine(vararg dependencies: Any?, fn: suspend CoroutineScope.()->Unit) =
    if (dependencies.isEmpty()) useCoroutineOnce(fn) // probably does not make sense to use version without dependencies
    else useEffectWithCleanup(*dependencies) { coroutineEffect(fn) }
/** Data-loading hook: a failed [Client.call] ([ApiError]) yields a null result
 *  instead of a toast — pages render their empty/loading state, background load
 *  failures don't nag. */
fun <T> useSuspendResult(vararg dependencies: Any?, resetOnChange: Boolean = true, f: suspend CoroutineScope.() -> T): T? {
    var res by useState<T?>(null)
    val load: suspend CoroutineScope.() -> Unit = {
        res = try { f() } catch (e: ApiError) { console.warn("load failed:", e.message); null }
    }
    if (dependencies.isEmpty())
        useCoroutineOnce(load)
    else
        useCoroutine(*dependencies) {
            if (resetOnChange) res = null
            load()
        }
    return res
}