@file:JsModule("@dnd-kit/utilities")
@file:JsNonModule

package dndkit.utilities

external interface Transform {
    var x: Number
    var y: Number
    var scaleX: Number
    var scaleY: Number
}

external interface Transition {
    var property: String
    var easing: String
    var duration: Number
}

external val CSS: Css

external interface ToStringable<T> {
    fun toString(value: T?): String
}

external interface Css {
    @JsName("Transition")
    val transition: ToStringable<Transition>

    @JsName("Transform")
    val transform: ToStringable<Transform>
}