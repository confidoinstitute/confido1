package hooks

import browser.window
import dom.html.HTMLDivElement
import dom.html.HTMLElement
import dom.observers.ResizeObserver
import react.*

data class ElementSize<T: HTMLElement>(
    val ref: RefObject<T>,
    val width: Double,
    val height: Double,
    val known: Boolean,
)

/**
 * Export a referenced element's size (width and height).
 */
fun <T: HTMLElement> useElementSize(): ElementSize<T> {
    var width by useState(0.0)
    var height by useState(0.0)
    var known by useState(false)

    val observer = useMemo {
        ResizeObserver { entries, _ ->
            entries.getOrNull(0)?.let {
                width = it.contentRect.width
                height = it.contentRect.height
                known = true
            }

        }
    }

    val ref = useRefEffect<T> {
        observer.observe(current)

        cleanup {
            observer.disconnect()
        }
    }

    return ElementSize(ref, width, height, known)
}

data class ViewportSize(val width: Int, val height: Int)
fun useViewportSize(): ViewportSize {
    val (width, setWidth) = useState(0)
    val (height, setHeight) = useState(0)

    val handleResize = useCallback { _: dynamic ->
        setWidth(window.innerWidth)
        setHeight(window.innerHeight)
    }

    useEffect {
        window.addEventListener("resize", handleResize)
        cleanup { window.removeEventListener("resize", handleResize) }
    }

    return ViewportSize(width, height)
}

