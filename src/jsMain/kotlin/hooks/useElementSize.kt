package hooks

import web.window.window
import web.html.HTMLDivElement
import web.html.HTMLElement
import react.*
import web.events.Event
import web.events.EventType
import web.events.RESIZE
import web.events.addEventListener
import web.events.removeEventListener
import web.resize.ResizeObserver

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
    val (width, setWidth) = useState { window.innerWidth }
    val (height, setHeight) = useState { window.innerHeight }

    val handleResize = useCallback { _: Event ->
        setWidth(window.innerWidth)
        setHeight(window.innerHeight)
    }

    useEffectWithCleanup {
        window.addEventListener(Event.RESIZE, handleResize)
        onCleanup { window.removeEventListener(Event.RESIZE, handleResize) }
    }

    return ViewportSize(width, height)
}

