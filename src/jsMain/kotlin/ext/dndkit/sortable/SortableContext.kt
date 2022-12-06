@file:JsModule("@dnd-kit/sortable")
@file:JsNonModule

package dndkit.sortable

import dndkit.utilities.Transform
import react.Context
import react.FC
import react.PropsWithChildren

external interface SortableContextProps : PropsWithChildren {
    var items: Array<dynamic /* String | Number | `T$0` */>
    var strategy: ((args: StrategyArgs) -> Transform?)? /*SortingStrategy?*/
    var id: String?
    var disabled: dynamic /* Boolean? | Disabled? */
}

/*
interface ContextDescriptor {
    activeIndex: number;
    containerId: string;
    disabled: Disabled;
    disableTransforms: boolean;
    items: UniqueIdentifier[];
    overIndex: number;
    useDragOverlay: boolean;
    sortedRects: ClientRect[];
    strategy: SortingStrategy;
}
 */

external val Context: Context<dynamic /* ContextDescriptor */>

@JsName("SortableContext")
external val SortableContext: FC<SortableContextProps>