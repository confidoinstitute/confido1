@file:JsModule("@dnd-kit/sortable")
@file:JsNonModule

package dndkit.sortable

external interface Disabled {
    var draggable: Boolean?
    var droppable: Boolean?
}

external fun <T> arrayMove(array: Array<T>, from: Number, to: Number): Array<T>
external fun <T> arraySwap(array: Array<T>, from: Number, to: Number): Array<T>

external interface Sortable {
    var containerId: dynamic /* String | Number */
    var items: Array<dynamic /* String | Number */>
    var index: Number
}

external interface SortableData {
    var sortable: Sortable
}

/*
typealias SortableTransition = Pick<Transition, String /* "easing" | "duration" */>

external interface `T$24` {
    var active: Active?
    var containerId: dynamic /* String | Number */
    var isDragging: Boolean
    var isSorting: Boolean
    var id: dynamic /* String | Number */
    var index: Number
    var items: Array<dynamic /* String | Number */>
    var previousItems: Array<dynamic /* String | Number */>
    var previousContainerId: dynamic /* String | Number */
    var newIndex: Number
    var transition: SortableTransition?
    var wasDragging: Boolean
}

typealias AnimateLayoutChanges = (args: `T$24`) -> Boolean

external interface NewIndexGetterArguments {
    var id: dynamic /* String | Number */
    var items: Array<dynamic /* String | Number */>
    var activeIndex: Number
    var overIndex: Number
}

typealias NewIndexGetter = (args: NewIndexGetterArguments) -> Number

 */