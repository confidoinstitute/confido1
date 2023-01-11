package dndkit

import dndkit.sortable.SyntheticListenerMap
import react.ChildrenBuilder


/** Applies listeners from [dndkit.sortable.useSortable]. */
fun ChildrenBuilder.applyListeners(listeners: SyntheticListenerMap?) {
    // SyntheticListenerMap is Record<string, Function>
    if (listeners != undefined) {
        val keys = js("Object").keys(listeners.asDynamic()).unsafeCast<Array<String>>()
        keys.map { this.asDynamic()[it] = listeners.asDynamic()[it] }
    }
}

