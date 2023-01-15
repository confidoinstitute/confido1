@file:JsModule("@dnd-kit/core")
@file:JsNonModule

package dndkit.core

import org.w3c.dom.events.Event

external interface DragEvent {
    var activatorEvent: Event
    var active: Active // Active
    var collisions: dynamic // Array<Collision>?
    var delta: dynamic // Translate
    var over: Over?
}

external interface DragStartEvent : DragEvent

external interface DragMoveEvent : DragEvent

external interface DragOverEvent : DragMoveEvent

external interface DragEndEvent : DragEvent

external interface DragCancelEvent : DragEndEvent