@file:JsModule("@dnd-kit/core")
@file:JsNonModule

package dndkit.core

external interface ClientRect {
    val width: Number
    val height: Number
    val top: Number
    val left: Number
    val right: Number
    val bottom: Number
}
