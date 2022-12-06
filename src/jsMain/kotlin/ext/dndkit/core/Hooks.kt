package dndkit.core

import org.w3c.dom.HTMLElement

@JsModule("@dnd-kit/core")
@JsNonModule
external interface ResizeObserverConfig {
    var disabled: Boolean?
    var updateMeasurementsFor: Array<dynamic /* String | Number */>?
    var timeout: Number?
}

@JsModule("@dnd-kit/core")
@JsNonModule
external interface UseDroppableArguments {
    var id: dynamic /* String | Number */
    var disabled: Boolean?
    var data: dynamic /* typealias AnyData = dynamic */
    var resizeObserverConfig: ResizeObserverConfig?
}

@JsModule("@dnd-kit/core")
@JsNonModule
external interface UseDraggableAttributes {
    var role: String?
    var roleDescription: String?
    var tabIndex: Number?
}

@JsModule("@dnd-kit/core")
@JsNonModule
external interface UseDraggableArguments {
    var id: dynamic /* String | Number */
    var data: dynamic /* typealias AnyData = dynamic */
    var disabled: Boolean?
    var attributes: UseDraggableAttributes?
}

@JsModule("@dnd-kit/core")
@JsNonModule
external enum class MeasuringStrategy {
    Always /* = 0 */,
    BeforeDragging /* = 1 */,
    WhileDragging /* = 2 */
}

@JsModule("@dnd-kit/core")
@JsNonModule
external enum class MeasuringFrequency {
    Optimized /* = "optimized" */
}

typealias MeasuringFunction = (element: HTMLElement) -> ClientRect

@JsModule("@dnd-kit/core")
@JsNonModule
external interface DroppableMeasuring {
    var measure: MeasuringFunction
    var strategy: MeasuringStrategy
    var frequency: dynamic /* dndkit.core.MeasuringFrequency | Number */
}
