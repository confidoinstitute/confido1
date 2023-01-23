package dndkit

import dndkit.sortable.SyntheticListenerMap
import kotlinx.js.Object
import react.ChildrenBuilder


/** Applies listeners from [dndkit.sortable.useSortable]. */
fun ChildrenBuilder.applyListeners(listeners: SyntheticListenerMap?) {
    // SyntheticListenerMap is Record<string, Function>
    if (listeners != undefined) {
        val keys = Object.keys(listeners)
        keys.map { this.asDynamic()[it] = listeners.asDynamic()[it] }
    }
}

