package tools.confido.web.ext.dndkit

import tools.confido.web.ext.dndkit.sortable.SyntheticListenerMap
import js.objects.Object
import react.ChildrenBuilder


/** Applies listeners from [tools.confido.web.ext.dndkit.sortable.useSortable]. */
fun ChildrenBuilder.applyListeners(listeners: SyntheticListenerMap?) {
    // SyntheticListenerMap is Record<string, Function>
    if (listeners != undefined) {
        val keys = Object.keys(listeners)
        keys.map { this.asDynamic()[it] = listeners.asDynamic()[it] }
    }
}

