package hooks

import dom.html.HTMLElement
import dom.observers.ResizeObserver
import react.*

data class ElementSize<T: HTMLElement>(
    val ref: Ref<T>,
    val width: Double,
    val height: Double,
)

/**
 * Export a referenced element's size (width and height).
 */
fun <T: HTMLElement> useElementSize(): ElementSize<T> {
    val ref = useRef<T>()
    var width by useState(0.0)
    var height by useState(0.0)

    val observer = useMemo {
        ResizeObserver { entries, _ ->
            entries.getOrNull(0)?.let {
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