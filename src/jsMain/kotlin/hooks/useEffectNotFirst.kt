package hooks

import react.CleanupBuilder
import react.useEffect
import react.useEffectWithCleanup
import react.useRef

fun useEffectNotFirst(vararg dependencies: Any?, cb: ()->Unit) {
    val isFirst = useRef(true)
    useEffect(*dependencies)  {
        if (isFirst.current == true) isFirst.current = false
        else cb()
    }
}
fun useEffectNotFirstWithCleanup(vararg dependencies: Any?, cb: CleanupBuilder.()->Unit) {
    val isFirst = useRef(true)
    useEffectWithCleanup(*dependencies)  {
        if (isFirst.current == true) isFirst.current = false
        else cb()
    }
}
