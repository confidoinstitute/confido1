package hooks

import js.objects.Object
import react.*
import js.array.component1
import js.array.component2

/**
 * A simple hook to trace which props caused rerender of a given component. For debugging purposes only.
 */
fun useTraceUpdate(props: Props) {
    val prev = useRef(props)

    useEffect {
        val prevProps = prev.current ?: return@useEffect

        val prevPropsEntries = Object.entries(prevProps).asList().associate { (name, value) -> name to value }
        val currentPropsEntries = Object.entries(props).associate { (name, value) -> name to value }

        val changedProps = currentPropsEntries.filter { (name, prop) ->
            prevPropsEntries[name] !== prop
        }
        if (changedProps.isNotEmpty()) {
            console.log("Rerender caused by:")
            changedProps.entries.map {
                console.log(it.key, it.value)
            }
        }
    }
}