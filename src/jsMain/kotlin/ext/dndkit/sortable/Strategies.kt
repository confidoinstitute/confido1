@file:JsModule("@dnd-kit/sortable")
@file:JsNonModule

package dndkit.sortable

import dndkit.core.ClientRect
import dndkit.utilities.Transform

external interface StrategyArgs {
    var activeNodeRect: ClientRect?
    var activeIndex: Number
    var index: Number
    var rects: Array<ClientRect>
    var overIndex: Number
}

//typealias SortingStrategy = (args: StrategyArgs) -> Transform?

external val horizontalListSortingStrategy: (args: StrategyArgs) -> Transform?

external val rectSortingStrategy: (args: StrategyArgs) -> Transform?

external val rectSwappingStrategy: (args: StrategyArgs) -> Transform?

external val verticalListSortingStrategy: (args: StrategyArgs) -> Transform?
