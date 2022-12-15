package hooks

import kotlinx.js.Object
import react.*

fun useTraceUpdate(props: Props) {
    val prev = useRef(props)

    useEffect {
        val prevProps = prev.current ?: return@useEffect

        val prevPropsEntries = Object.entries(prevProps).associate { (name, value) -> name to value }
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