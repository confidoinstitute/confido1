@file:JsModule("@dnd-kit/sortable")
@file:JsNonModule

package tools.confido.web.ext.dndkit.sortable

import tools.confido.web.ext.dndkit.core.ClientRect
import tools.confido.web.ext.dndkit.utilities.Transform

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
