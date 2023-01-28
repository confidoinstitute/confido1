@file:JsModule("@dnd-kit/core")
@file:JsNonModule

package dndkit.core

import org.w3c.dom.events.KeyboardEvent

external fun <T : SensorOptions> useSensor(sensor: dynamic /*Sensor<T>*/, options: T = definedExternally): SensorDescriptor<T>

external fun useSensors(vararg sensors: SensorDescriptor<dynamic /* Any*/>?): Array<SensorDescriptor<SensorOptions>>

external interface KeyboardEventContainer {
    var event: KeyboardEvent
}

external interface SensorOptions // = {}

external interface SensorDescriptor<T> {
    var sensor: Sensor<T>
    var options: T
}

external interface KeyboardSensorOptions : SensorOptions {
    /*
    var keyboardCodes: KeyboardCodes?
    var coordinateGetter: KeyboardCoordinateGetter?
    */
    var scrollBehavior: String? /* "auto" | "smooth" */
    val onActivation: ((ev: KeyboardEventContainer) -> Unit)?
}

external interface Sensor<T> {
    fun new(props: SensorProps<T>): SensorInstance
}
/*
export interface Sensor<T extends Object> {
  new (props: SensorProps<T>): SensorInstance;
  activators: Activators<T>;
  setup?(): Teardown | undefined;
}
 */

open external class SensorInstance {
    open var autoScrollEnabled: Boolean;
}


external interface SensorProps<T> {
    /*
    var active: UniqueIdentifier;
    var activeNode: DraggableNode;
    var event: Event;
    var context: MutableRefObject<SensorContext>;
     */
    var options: T
    /*
    var onStart(coordinates: Coordinates): void;
    var onCancel(): void;
    var onMove(coordinates: Coordinates): void;
    var onEnd(): void;
     */
}

external interface AbstractPointerSensorOptions : SensorOptions {
    /*
    activationConstraint?: PointerActivationConstraint;
    onActivation?({event}: {event: Event}): void;
     */
}
open external class AbstractPointerSensor : SensorInstance

external interface MouseSensorOptions : AbstractPointerSensorOptions
//external class MouseSensor(props: SensorProps<MouseSensorOptions>) : AbstractPointerSensor {
external val MouseSensor : AbstractPointerSensor

open external class KeyboardSensor(props: SensorProps<KeyboardSensorOptions>) : SensorInstance {
    open var props: Any
    override var autoScrollEnabled: Boolean
    open var referenceCoordinates: Any
    open var listeners: Any
    open var windowListeners: Any
    open var attach: Any
    open var handleStart: Any
    open var handleKeyDown: Any
    open var handleMove: Any
    open var handleEnd: Any
    open var handleCancel: Any
    open var detach: Any

    companion object {
        //var activators: Activators<KeyboardSensorOptions>
    }
}

/*
external interface KeyboardCodes {
    var start: Array<String>
    var cancel: Array<String>
    var end: Array<String>
}

external interface `T$21` {
    var active: dynamic /* String | Number */
        get() = definedExternally
        set(value) = definedExternally
    var currentCoordinates: Coordinates
    var context: SensorContext
}

typealias KeyboardCoordinateGetter = (event: KeyboardEvent, args: `T$21`) -> dynamic
*/