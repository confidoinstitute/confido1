package hooks

import org.w3c.dom.HTMLElement
import org.w3c.dom.ResizeObserver
import react.*

data class ElementSize<T: HTMLElement>(
    val ref: Ref<T>,
    val width: Double,
    val height: Double,
)

fun <T: HTMLElement> useElementSize(): ElementSize<T> {
    val ref = useRef<T>()
    var width by useState(0.0)
    var height by useState(0.0)

    val observer = useMemo {
        ResizeObserver { entries, _ ->
            val rect = entries.getOrNull(0)?.let {
                width = it.contentRect.width
                height = it.contentRect.height
            }

        }
    }

    useEffect(ref.current) {
        ref.current?.let {
            observer.observe(it)
        }

        cleanup {
            observer.disconnect()
        }
    }

    return ElementSize(ref, width, height)
}