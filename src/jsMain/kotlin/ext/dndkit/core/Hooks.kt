@file:JsModule("@dnd-kit/core")
@file:JsNonModule

package dndkit.core

import org.w3c.dom.HTMLElement

external interface ResizeObserverConfig {
    var disabled: Boolean?
    var updateMeasurementsFor: Array<dynamic /* String | Number */>?
    var timeout: Number?
}

external interface UseDroppableArguments {
    var id: dynamic /* String | Number */
    var disabled: Boolean?
    var data: dynamic /* typealias AnyData = dynamic */
    var resizeObserverConfig: ResizeObserverConfig?
}

external interface UseDraggableAttributes {
    var role: String?
    var roleDescription: String?
    var tabIndex: Number?
}

external interface UseDraggableArguments {
    var id: dynamic /* String | Number */
    var data: dynamic /* typealias AnyData = dynamic */
    var disabled: Boolean?
    var attributes: UseDraggableAttributes?
}

external enum class MeasuringStrategy {
    Always /* = 0 */,
    BeforeDragging /* = 1 */,
    WhileDragging /* = 2 */
}

external enum class MeasuringFrequency {
    Optimized /* = "optimized" */
}

external interface DroppableMeasuring {
    var measure: (element: HTMLElement) -> ClientRect
    var strategy: MeasuringStrategy
    var frequency: dynamic /* dndkit.core.MeasuringFrequency | Number */
}
