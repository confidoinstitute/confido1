package hooks

import react.useEffect
import react.useEffectOnce
import react.useRef

/**
 * Call the callback with a given value when the component unmounts. Can be useful in combination with `useDebounce`.
 */
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