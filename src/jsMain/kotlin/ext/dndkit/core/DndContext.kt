@file:JsModule("@dnd-kit/core")
@file:JsNonModule

package dndkit.core

import dndkit.utilities.Transform
import react.Context
import react.NamedExoticComponent
import react.PropsWithChildren

/*
external interface DndContextAccessibility {
    var announcements: Announcements?
    var container: Element?
    var restoreFocus: Boolean?
    var screenReaderInstructions: ScreenReaderInstructions?
}
*/

external interface DndContextProps : PropsWithChildren {
    var id: String?
    var accessibility: dynamic // DndContextAccessibility?
    var autoScroll: dynamic /* Boolean? | AutoScrollOptions? */
    var cancelDrop: ((args: CancelDropArguments) -> dynamic)? /* Boolean | Promise<boolean> */
    var collisionDetection: dynamic // CollisionDetection?
    var measuring: dynamic // MeasuringConfiguration?
    var modifiers: dynamic // Modifiers?
    var sensors: dynamic // Array<SensorDescriptor<Any>>?
    var onDragStart: ((event: DragStartEvent) -> Unit)?
    var onDragMove: ((event: DragMoveEvent) -> Unit)?
    var onDragOver: ((event: DragOverEvent) -> Unit)?
    var onDragEnd: ((event: DragEndEvent) -> Unit)?
    var onDragCancel: ((event: DragCancelEvent) -> Unit)?
}

external interface CancelDropArguments : DragEndEvent

external val ActiveDraggableContext: Context<Transform>
external val DndContext: NamedExoticComponent<DndContextProps>

external fun useDndContext(): Any // stores.PublicContextDescriptor
