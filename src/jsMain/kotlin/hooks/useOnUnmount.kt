package hooks

import react.useEffect
import react.useEffectOnce
import react.useRef

fun <T: Any> useOnUnmount(value: T?, callback: ((T) -> Unit)) {
    val ref = useRef(value)
    useEffect(value) {
        ref.current = value
    }
    useEffectOnce {
        cleanup {
            ref.current?.let { callback(it) }
        }
    }
}