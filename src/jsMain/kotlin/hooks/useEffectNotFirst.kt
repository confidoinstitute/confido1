package hooks

import react.EffectBuilder
import react.useEffect
import react.useRef

fun useEffectNotFirst(vararg dependencies: Any?, cb: EffectBuilder.()->Unit) {
    val isFirst = useRef(true)
    useEffect(*dependencies)  {
        if (isFirst.current == true) isFirst.current = false
        else cb()
    }
}