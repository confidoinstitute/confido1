@file:JsModule("@dnd-kit/sortable")
@file:JsNonModule

package dndkit.sortable

import dndkit.core.ResizeObserverConfig
import dndkit.core.UseDraggableAttributes
import org.w3c.dom.HTMLElement
import dndkit.utilities.Transform

external interface UseSortableArguments {
    // The following is Omit<UseDraggableArguments, 'disabled'>
    var id: dynamic /* String | Number */
    var data: dynamic /* typealias AnyData = dynamic */
    var attributes: UseDraggableAttributes?
    // end of Omit

    // The following is from Pick<UseDroppableArguments, resizeObserverConfig>
    var resizeObserverConfig: ResizeObserverConfig?

    var animateLayoutChanges: dynamic // AnimateLayoutChanges?
    var getNewIndex: dynamic // NewIndexGetter?
    var strategy: ((args: StrategyArgs) -> Transform?)? /* SortingStrategy? */
    var transition: dynamic // SortableTransition?
}

// Record<string, function>
external interface SyntheticListenerMap

external interface UseSortable {
    var active: Any?
    var activeIndex: Number
    var attributes: Any
    var data: SortableData /* SortableData & Json */
    var rect: Any
    var index: Number
    var newIndex: Number
    var items: Array<Any>
    var isOver: Boolean
    var isSorting: Boolean
    var isDragging: Boolean
    var listeners: SyntheticListenerMap? /* SyntheticListenerMap | undefined */
    var node: Any
    var overIndex: Number
    var over: Any?
    var setNodeRef: (node: HTMLElement?) -> Unit
    var setActivatorNodeRef: (element: HTMLElement?) -> Unit
    var setDroppableNodeRef: (element: HTMLElement?) -> Unit
    var setDraggableNodeRef: (element: HTMLElement?) -> Unit
    var transform: Transform?
    var transition: String?
}

external fun useSortable(args: UseSortableArguments): UseSortable
